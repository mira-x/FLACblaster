package eu.mordorx.flacblaster.fs

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.ParcelFileDescriptor
import android.util.Log
import com.simplecityapps.ktaglib.KTagLib
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

    /** Starts a scan in a new thread */
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
        Log.d("MediaScannerSingleton", "Starting scan transaction in mode ${scanMode.value.name}...")
        scanStateProgress.value = 0f
        scanStateLabel.value = ""
        scanState.value = true
        db().runInTransaction {
            Log.d("MediaScannerSingleton", "Starting scan phase 1 in mode ${scanMode.value.name}...")
            scanStateLabel.value = "(1/4) Looking for new files..."
            val filesAndFoldersToCheck = scanPhase1()
            filesAndFoldersToCheck.forEach {
                if (it.isDirectory)
                Log.d("MediaScannerSingleton", "Phase 1 in mode ${scanMode.value.name} found folder ${it.absolutePath}")
            }

            if(filesAndFoldersToCheck.isEmpty()) {
                return@runInTransaction
            }

            Log.d("MediaScannerSingleton", "1 Found rootDir: " + (db().fileEntityDao().getFileByPath("/storage/emulated/0/Music")?.getName() ?: "PRANK BRO"))


            Log.d("MediaScannerSingleton", "Starting scan phase 2 in mode ${scanMode.value.name}...")
            scanStateLabel.value = "(2/4) Reading file metadata..."
            val updatedSongs = scanPhase2(filesAndFoldersToCheck)

            Log.d("MediaScannerSingleton", "2 Found rootDir: " + (db().fileEntityDao().getFileByPath("/storage/emulated/0/Music")?.getName() ?: "PRANK BRO"))

            Log.d("MediaScannerSingleton", "Starting scan phase 3 in mode ${scanMode.value.name}...")
            scanStateLabel.value = "(3/4) Collecting folder metadata..."
            scanPhase3(updatedSongs)

            Log.d("MediaScannerSingleton", "3 Found rootDir: " + (db().fileEntityDao().getFileByPath("/storage/emulated/0/Music")?.getName() ?: "PRANK BRO"))

            Log.d("MediaScannerSingleton", "Starting scan phase 4 in mode ${scanMode.value.name}...")
            scanStateLabel.value = "(4/4) Purging DB..."
            scanPhase4(filesAndFoldersToCheck)

            Log.d("MediaScannerSingleton", "4 Found rootDir: " + (db().fileEntityDao().getFileByPath("/storage/emulated/0/Music")?.getName() ?: "PRANK BRO"))
        }
        scanStateLabel.value = ""
        scanState.value = false
        Log.d("MediaScannerSingleton", "Committing scan transaction...")
    }

    /** Use this for progress bars */
    fun <T> Collection<T>.forEachWithProgressParallel(action: (T) -> Unit) {
        val counter = AtomicInteger(0)
        parallelStream().forEach {
            action(it)
            scanStateProgress.value = counter.incrementAndGet().toFloat() / size.toFloat()
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

    /** Returns all parent directories up to the specified root directory, including the root directory */
    fun File.allParents(rootDir: File): Set<File> {
        val out = mutableSetOf(rootDir)

        var current = this.parentFile
        while (current != null && current != rootDir) {
            out.add(current)
            current = current.parentFile
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
        // FAST mode requires that at least the root music folder is inside the DB. Thus the first scan must be in CORRECT mode.
        if (scanMode.value == MediaScanMode.CORRECT || db().fileEntityDao().getFileEntityCount() == 0) {
            val rootDir = ctx.getSharedPreferences(ctx.packageName, MODE_PRIVATE).getString("RootDirectory", "")!!
            if (rootDir.isEmpty()) {
                Log.e("MediaScannerSingleton", "No music root directory is set!")
                return hashSetOf()
            }

            return File(rootDir).listFilesRecursively().toHashSet()
        } else if (scanMode.value == MediaScanMode.FAST) {
            val existingFolderEntities = db().fileEntityDao().getAllFiles(true)
            val modifiedPaths = ConcurrentHashMap.newKeySet<File>()
            existingFolderEntities.forEachWithProgressParallel { existingFolderEntity ->
                val existingFolder = File(existingFolderEntity.path)
                if (!existingFolder.exists()) {
                    modifiedPaths.add(existingFolder)
                    return@forEachWithProgressParallel
                }
                if (existingFolder.lastModified() == existingFolderEntity.lastModifiedMs) {
                    return@forEachWithProgressParallel
                }
                val directChildren = existingFolder.listFilesRecursively().filter { !it.isHidden }
                modifiedPaths.addAll(directChildren)
            }

            return modifiedPaths.toHashSet()
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
        val changedFileEntities = ConcurrentHashMap.newKeySet<FileEntity>()
        val changedFiles = ConcurrentHashMap.newKeySet<File>()

        modifiedPathsSet
            .filter(File::isFile)
            .forEachWithProgressParallel { f ->
                val entity = allFileEntities.find { entity -> entity.path == f.absolutePath } ?: FileEntity.emptyOfFile(f)
                if (f.lastModified() == entity.lastModifiedMs && f.length() == entity.size) {
                    return@forEachWithProgressParallel
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

        return changedFiles.toHashSet()
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
        val sortedSongs = db().fileEntityDao().getAllFiles(false).sortedBy { it.path }

        modifiedFolders.forEachWithProgressParallel { modifiedFolder ->
            val folderPathWithSlash = modifiedFolder.absolutePath +
                    if (!modifiedFolder.absolutePath.endsWith("/")) "/" else ""

            val entity = db().fileEntityDao().getFileByPath(modifiedFolder.absolutePath) ?: FileEntity.emptyOfFile(modifiedFolder)

            val startIdx = sortedSongs.binarySearch {
                it.path.compareTo(folderPathWithSlash)
            }.let { if (it < 0) -(it + 1) else it }

            var size = 0L
            var duration = 0
            var count = 0

            // Aggregate size, duration and count until the folder changes
            for (i in startIdx until sortedSongs.size) {
                val song = sortedSongs[i]
                if (!song.path.startsWith(folderPathWithSlash)) break

                size += song.size
                duration += song.durationMs
                count++
            }

            entity.apply {
                lastModifiedMs = modifiedFolder.lastModified()
                this.size = size
                durationMs = duration
                childCount = count
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

        possiblyDeleted.toList().forEachWithProgressParallel {
            if (!File(it.path).exists()) {
                deleted.add(it)
            }
        }

        db().fileEntityDao().delete(*deleted.toTypedArray())
    }

}