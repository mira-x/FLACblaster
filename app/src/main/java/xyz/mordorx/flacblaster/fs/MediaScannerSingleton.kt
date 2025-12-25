package xyz.mordorx.flacblaster.fs

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.HashMap
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

    fun scanAsync() {
        if (scanState.value) {
            return
        }
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
            Log.d("MediaScannerSingleton", "Starting scan phase 1...")
            scanStateLabel.value = "(1/5) Setting up DB..."
            scanPhase1()
            Thread.sleep(1000)

            Log.d("MediaScannerSingleton", "Starting scan phase 2...")
            scanStateLabel.value = "(2/5) Looking for new files..."
            val filesAndFoldersToCheck = scanPhase2()
            filesAndFoldersToCheck.forEach {
                Log.d("MediaScannerSingleton", "Phase 2 found: " + it.absolutePath)
            }

            if(filesAndFoldersToCheck.size == 0) {
                return@runInTransaction
            }

            Log.d("MediaScannerSingleton", "Starting scan phase 3...")
            scanStateLabel.value = "(3/5) Reading file metadata..."
            scanPhase3(filesAndFoldersToCheck)

            Log.d("MediaScannerSingleton", "Starting scan phase 4...")
            scanStateLabel.value = "(4/5) Collecting folder metadata..."
            scanPhase4(filesAndFoldersToCheck)

            Log.d("MediaScannerSingleton", "Starting scan phase 5...")
            scanStateLabel.value = "(5/5) Purging DB..."
            scanPhase5(filesAndFoldersToCheck)
        }
        scanState.value = false
        scanStateLabel.value = ""
        Log.d("MediaScannerSingleton", "Committing scan transaction...")
    }

    /** Use this for progress bars */
    fun <T> List<T>.forEachWithProgress(action: (T) -> Unit) {
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
     * @throws RuntimeException
     */
    private fun scanPhase1() {
        val existing = db().fileEntityDao().getFileEntityCount()
        if (existing > 0) {
            return
        }
        val rootDir = ctx.getSharedPreferences(ctx.packageName, MODE_PRIVATE).getString("RootDirectory", "")!!
        if (rootDir.isEmpty()) {
            throw RuntimeException("No music root directory is set")
        }

        val rootEntity = FileEntity(
            path = rootDir,
            isFolder = true,
            lastModifiedMs = 0,
            size = 0,
            childCount = 0,
            durationMs = 0,
            metadata = HashMap()
        )
        db().fileEntityDao().upsert(rootEntity)
    }

    private fun scanPhase2(): HashSet<File> {
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

    /**
     * @param modifiedPathsSet A Set of files that may or may not exist and may or may not be modified
     */
    private fun scanPhase3(modifiedPathsSet: HashSet<File>) {
        val allFileEntities = db().fileEntityDao().getAllFiles(false)
        val entityMap = modifiedPathsSet.associate { file ->
            file to (allFileEntities.find { entity -> entity.path == file.absolutePath } ?: FileEntity.m1OfFile(file))
        }
        entityMap.forEachWithProgress { f, entity ->
            if (f.lastModified() == entity.lastModifiedMs && f.length() == entity.size) {
                return@forEachWithProgress
            }

            entity.apply {
                lastModifiedMs = f.lastModified()
                size = f.length()
                // TODO: Add M2 and M3 metadata
            }

        }

        db().fileEntityDao().upsert(*entityMap.values.toTypedArray())
    }

    /**
     * @param modifiedPathsSet A Set of files that may or may not exist and may or may not be modified
     */
    private fun scanPhase4(modifiedPathsSet: HashSet<File>) {
        val allEntities = db().fileEntityDao().getAllFiles()
        val allFolderEntities = allEntities.stream().filter { it.isFolder }.collect(Collectors.toList())
        val folderEntityMap = modifiedPathsSet.associate { file ->
            file to (allFolderEntities.find { entity -> entity.path == file.absolutePath } ?: FileEntity.m1OfFile(file))
        }

        folderEntityMap.forEachWithProgress { folder, entity ->
            val recursiveChildrenEntities = allEntities
                .stream()
                .filter { it.path.startsWith(folder.path) && !it.isFolder }
                .collect(Collectors.toList())

            entity.apply {
                lastModifiedMs = folder.lastModified()
                size = recursiveChildrenEntities.sumOf(FileEntity::size)
                durationMs = recursiveChildrenEntities.sumOf(FileEntity::durationMs)
                childCount = recursiveChildrenEntities.size
            }
        }

        db().fileEntityDao().upsert(*folderEntityMap.values.toTypedArray())
    }

    private fun scanPhase5(modifiedPathsSet: HashSet<File>) {
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