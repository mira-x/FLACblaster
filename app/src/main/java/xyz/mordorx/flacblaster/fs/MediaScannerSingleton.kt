package xyz.mordorx.flacblaster.fs

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.ParcelFileDescriptor
import android.util.Log
import com.simplecityapps.ktaglib.KTagLib
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.stream.Collectors
import kotlin.collections.forEachIndexed

/**
 * This is the singleton that scans for changes to the FS and writes them to the DB.
 *
 * To better understand this, please read ARCHITECTURE.md.
 **/
class MediaScannerSingleton private constructor(val ctx: Context) {
    companion object {
        private var singleton: MediaScannerSingleton? = null
        fun get(ctx: Context): MediaScannerSingleton {
            return singleton ?: MediaScannerSingleton(ctx)
        }
    }

    private fun db(): DatabaseSingleton = DatabaseSingleton.get(ctx)

    val scanStateProgress = MutableStateFlow(0f)
    val scanStateLabel = MutableStateFlow("")
    val scanState = MutableStateFlow(false)
    val scanMode = MutableStateFlow(MediaScanMode.CORRECT)

    fun scanAsync(mode: MediaScanMode) {
        if (scanState.value) {
            return
        }
        scanMode.value = mode
        Thread(this::scan).start()
    }

    /**
     * Don't run on UI thread! It *will* crash
     **/
    private fun scan() {
        Log.d("MediaScannerSingleton", "Starting scan transaction...")
        scanStateProgress.value = 0f
        scanStateLabel.value = ""
        scanState.value = true
        db().runInTransaction {
            Log.d("MediaScannerSingleton", "Starting scan phase 1 in mode ${scanMode.value.name}...")
            scanStateLabel.value = "(1/4) Looking for new files..."
            val filesAndFoldersToCheck = scanPhase1()

            if(filesAndFoldersToCheck.isEmpty()) {
                return@runInTransaction
            }

            Log.d("MediaScannerSingleton", "Starting scan phase 2...")
            scanStateLabel.value = "(2/4) Reading file metadata..."
            val updatedSongs = scanPhase2(filesAndFoldersToCheck)

            Log.d("MediaScannerSingleton", "Starting scan phase 3...")
            scanStateLabel.value = "(3/4) Collecting folder metadata..."
            scanPhase3(updatedSongs)

            Log.d("MediaScannerSingleton", "Starting scan phase 4...")
            scanStateLabel.value = "(4/4) Purging DB..."
            scanPhase4(filesAndFoldersToCheck)
        }
        scanState.value = false
        scanStateLabel.value = ""
        Log.d("MediaScannerSingleton", "Committing scan transaction...")
    }

    /** Use this for progress bars */
    fun <T> Collection<T>.forEachWithProgress(action: (T) -> Unit) {
        forEachIndexed { index, item ->
            action(item)
            scanStateProgress.value = (index + 1f) / size
        }
    }

    /** Use this for progress bars */
    fun <K, V> Map<K, V>.forEachWithProgress(action: (K, V) -> Unit) {
        var index = 0
        forEach { (key, value) ->
            action(key, value)
            scanStateProgress.value = (++index).toFloat() / size
        }
    }

    /** Common music file extensions in lowercase. Ordered from most common to least common. */
    val audioExtensions = setOf(
        ".flac", ".mp3", ".m4a", ".ogg", ".opus", ".wav", ".aac", ".wma", ".alac", ".ape", ".wv", ".aiff", ".mka", ".dsf", ".dff", ".mpc", ".tta", ".webm"
    )

    /** Returns true if the file has a valid audio file extension, like ".flac" */
    fun File.isAudioFile(): Boolean {
        return audioExtensions.find { ext -> this.absolutePath.endsWith(ext, true) } != null
    }

    /** Returns all parent directories up the specified root directory */
    fun File.allParents(rootDir: File): Set<File> {
        val out = mutableSetOf(this)
        if (this.parentFile != null && this.parentFile != rootDir) {
            out.addAll(this.parentFile!!.allParents(rootDir))
        }
        return out
    }

    /** Always returns this file and all child elements, excluding hidden files */
    fun File.listFilesRecursively(): Set<File> {
        val out = mutableSetOf<File>()
        out.add(this)
        val children = this.listFiles() ?: return out

        children.forEach {
            if (it.isHidden || (it.isFile && !it.isAudioFile())) {
                return@forEach
            }
            when (it.isFile) {
                true -> out.add(it)
                false -> out.addAll(it.listFilesRecursively())
            }
        }
        return out
    }

    /**
     * @return HashSet<File> of files and folders that should be scanned for changes.
     */
    private fun scanPhase1(): HashSet<File> {
        // FAST mode requires that at least the root music folder is inside the DB

        if (scanMode.value == MediaScanMode.CORRECT || db().fileEntityDao().getFileEntityCount() == 0) {
            val rootDir = ctx.getSharedPreferences(ctx.packageName, MODE_PRIVATE).getString("RootDirectory", "")!!
            if (rootDir.isEmpty()) {
                Log.e("MediaScannerSingleton", "No music root directory is set!")
                return hashSetOf()
            }

            return File(rootDir).listFilesRecursively().toHashSet()
        } else if (scanMode.value == MediaScanMode.FAST) {
            val existingFolderEntities = db().fileEntityDao().getAllFiles(true)
            val modifiedPaths = HashSet<File>()
            existingFolderEntities.forEachWithProgress { existingFolderEntity ->
                val existingFolder = File(existingFolderEntity.path)
                if (!existingFolder.exists()) {
                    modifiedPaths.add(existingFolder)
                    return@forEachWithProgress
                }
                if (existingFolder.lastModified() == existingFolderEntity.lastModifiedMs) {
                    return@forEachWithProgress
                }
                val directChildren = existingFolder.listFilesRecursively().filter { !it.isHidden }
                modifiedPaths.addAll(directChildren)
            }

            return modifiedPaths
        }

        throw RuntimeException("Unreachable code")
    }

    /**
     * @param modifiedPathsSet A Set of files to scan. Folders are ignored.
     * @return HashSet<File> of all files (no folders) whose metadata has changed.
     */
    private fun scanPhase2(modifiedPathsSet: HashSet<File>): HashSet<File> {
        val taglib = KTagLib()
        val allFileEntities = db().fileEntityDao().getAllFiles(false)
        val changedFileEntities = HashSet<FileEntity>()
        val changedFiles = HashSet<File>()

        modifiedPathsSet
            .filter(File::isFile)
            .forEachWithProgress { f ->
                val entity = allFileEntities.find { entity -> entity.path == f.absolutePath } ?: FileEntity.emptyOfFile(f)
                if (f.lastModified() == entity.lastModifiedMs && f.length() == entity.size) {
                    return@forEachWithProgress
                }

                entity.lastModifiedMs = f.lastModified()
                entity.size = f.length()

                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val rawFd = pfd.detachFd()
                val meta = try {
                    taglib.getMetadata(rawFd, f.name)
                } finally {
                    pfd.close()
                }

                meta?.propertyMap?.let { entity.metadata = it }
                meta?.audioProperties?.let {
                    entity.channelCount = it.channelCount
                    entity.bitrateKbps = it.bitrate
                    entity.sampleRateHz = it.sampleRate
                    entity.durationMs = it.duration
                }

                changedFileEntities.add(entity)
                changedFiles.add(f)
            }

        db().fileEntityDao().upsert(*changedFileEntities.toTypedArray())

        return changedFiles
    }

    /**
     * @param modifiedFiles A Set of files (not folders) that were modified.
     */
    private fun scanPhase3(modifiedFiles: HashSet<File>) {
        val rootDirPath = ctx.getSharedPreferences(ctx.packageName, MODE_PRIVATE).getString("RootDirectory", "")!!
        if (rootDirPath.isEmpty()) {
            throw RuntimeException("No music root directory is set")
        }
        val rootDir = File(rootDirPath)

        /** This contains all directories whose aggregate values have to be re-calculated */
        val modifiedFolders = mutableSetOf<File>()
        modifiedFiles.forEach {
            modifiedFolders.addAll(it.allParents(rootDir))
        }

        val modifiedFoldersEntities = mutableListOf<FileEntity>()

        val allSongs = db().fileEntityDao().getAllFiles(false)
        modifiedFolders.forEachWithProgress { modifiedFolder ->
            val folderPathWithSlash = modifiedFolder.absolutePath + when (modifiedFolder.absolutePath.endsWith("/")) { false -> "/" true -> "" }
            val entity = db().fileEntityDao().getFileByPath(modifiedFolder.absolutePath) ?: FileEntity.emptyOfFile(modifiedFolder)
            val recursiveChildren = allSongs
                .stream()
                .filter { it.path.startsWith(folderPathWithSlash) }
                .collect(Collectors.toList())

            entity.apply {
                lastModifiedMs = modifiedFolder.lastModified()
                size = recursiveChildren.sumOf(FileEntity::size)
                durationMs = recursiveChildren.sumOf(FileEntity::durationMs)
                childCount = recursiveChildren.size
            }

            modifiedFoldersEntities.add(entity)
        }

        db().fileEntityDao().upsert(*modifiedFoldersEntities.toTypedArray())
    }

    /**
     * @param modifiedPathsSet A Set of files and folders that may or may not exist and may or may not be modified
     */
    private fun scanPhase4(modifiedPathsSet: HashSet<File>) {
        // Deleted files won't shop up in modifiedPathsSet, but their folders!
        val modifiedFolders = modifiedPathsSet.filter { it.isDirectory }

        val possiblyDeleted = mutableSetOf<FileEntity>()
        modifiedFolders.forEach { folder ->
            possiblyDeleted.addAll(db().fileEntityDao().getAllFilesPrefixed(folder.absolutePath))
        }

        val deleted = mutableListOf<FileEntity>()

        possiblyDeleted.toList().forEachWithProgress {
            if (!File(it.path).exists()) {
                deleted.add(it)
            }
        }

        db().fileEntityDao().delete(*deleted.toTypedArray())
    }

}