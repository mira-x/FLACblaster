package xyz.mordorx.flacblaster.fs

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.HashMap
import java.util.stream.Collectors

class ScannerService : Service() {
    inner class LocalBinder : Binder() {
        fun getService(): ScannerService = this@ScannerService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun db(): DatabaseSingleton = DatabaseSingleton.get(applicationContext)
    val scanProcessProgress = MutableStateFlow(0f)
    val scanProcessLabel = MutableStateFlow("")

    fun helloWorld() {
        Log.i("ScannerService", "HI!!!!")
        val scanThread = Thread(this::scan).start()
    }

    /** Don't run on UI thread! It *will* crash */
    fun scan() {
        Log.d("ScannerService", "Starting scan transaction...")
        db().runInTransaction {
            Log.d("ScannerService", "Starting scan phase 1...")
            scanPhase1()
            Log.d("ScannerService", "Starting scan phase 2...")
            val filesAndFoldersToCheck = scanPhase2()
            filesAndFoldersToCheck.forEach {
                Log.d("ScannerService", "Phase 2 found: " + it.absolutePath)
            }
            Log.d("ScannerService", "Starting scan phase 3...")
            scanPhase3(filesAndFoldersToCheck)
            Log.d("ScannerService", "Starting scan phase 4...")
            scanPhase4(filesAndFoldersToCheck)
            Log.d("ScannerService", "Starting scan phase 5...")
            scanPhase5()
        }
        Log.d("ScannerService", "Committing scan transaction...")
    }

    /** Use this for progress bars */
    fun <T> List<T>.forEachWithProgress(
        progress: MutableStateFlow<Float>,
        action: (T) -> Unit
    ) {
        forEachIndexed { index, item ->
            action(item)
            progress.value = (index + 1f) / size
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
    fun scanPhase1() {
        val existing = db().fileEntityDao().getFileEntityCount()
        if (existing > 0) {
            return
        }
        val rootDir = getSharedPreferences(packageName, MODE_PRIVATE).getString("RootDirectory", "")!!
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

    fun scanPhase2(): HashSet<File> {
        val existingFolderEntities = db().fileEntityDao().getAllFiles(true)
        val modifiedPaths = HashSet<File>()
        existingFolderEntities.forEach { existingFolderEntity ->
            val existingFolder = File(existingFolderEntity.path)
            if (!existingFolder.exists()) {
                modifiedPaths.add(existingFolder)
                return@forEach
            }
            if (existingFolder.lastModified() == existingFolderEntity.lastModifiedMs) {
                return@forEach
            }
            val directChildren = existingFolder.listFilesRecursively().filter { !it.isHidden }
            modifiedPaths.addAll(directChildren)
        }

        return modifiedPaths
    }

    /**
     * @param modifiedPathsSet A Set of files that may or may not exist and may or may not be modified
     */
    fun scanPhase3(modifiedPathsSet: HashSet<File>) {
        val allFileEntites = db().fileEntityDao().getAllFiles(false)
        val entityMap = modifiedPathsSet.associate { file ->
            file to (allFileEntites.find { entity -> entity.path == file.absolutePath } ?: FileEntity.m1OfFile(file))
        }
        entityMap.forEach { (f, entity) ->
            if (f.lastModified() == entity.lastModifiedMs && f.length() == entity.size) {
                return@forEach
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
    fun scanPhase4(modifiedPathsSet: HashSet<File>) {
        val allEntities = db().fileEntityDao().getAllFiles()
        val allFolderEntities = allEntities.stream().filter { it.isFolder }.collect(Collectors.toList())
        val folderEntityMap = modifiedPathsSet.associate { file ->
            file to (allFolderEntities.find { entity -> entity.path == file.absolutePath } ?: FileEntity.m1OfFile(file))
        }

        folderEntityMap.forEach { (folder, entity) ->
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

    fun scanPhase5() {
        val deleted = db()
            .fileEntityDao()
            .getAllFiles()
            .parallelStream()
            .filter { !File(it.path).exists() }
            .collect(Collectors.toList())
            .toTypedArray()

        db().fileEntityDao().delete(*deleted)
    }

}