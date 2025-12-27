package xyz.mordorx.flacblaster.fs

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FileEntityDao {
    @Query("SELECT * FROM files")
    fun getAllFiles(): List<FileEntity>

    @Query("SELECT * FROM files WHERE isFolder = :isFolder")
    fun getAllFiles(isFolder: Boolean): List<FileEntity>

    // GLOB is case-sensitive unlike LIKE. We are on Android/Linux/Unix so capitalization matters
    @Query("SELECT * FROM files WHERE path GLOB :prefix || '*'")
    fun getAllFilesPrefixed(prefix: String): List<FileEntity>

    @Upsert
    fun upsert(vararg files: FileEntity)

    @Query("SELECT * FROM files WHERE path = :path")
    fun getFileByPath(path: String): FileEntity?

    @Query("SELECT * FROM files WHERE path = :path")
    fun getFlowByPath(path: String): Flow<FileEntity?>

    @Query("SELECT * FROM files WHERE path GLOB :folderPath || '/*' AND path NOT GLOB :folderPath || '/*/*' ORDER BY isFolder DESC, path ASC")
    fun getDirectChildren(folderPath: String): Flow<List<FileEntity>>

    @Query("SELECT COUNT(*) FROM files")
    fun getFileEntityCount(): Int

    @Delete
    fun delete(vararg files: FileEntity)

    @Query("SELECT * FROM files ORDER BY path DESC")
    fun getAllFilesFlow(): Flow<List<FileEntity>>
}