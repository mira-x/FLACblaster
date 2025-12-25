package xyz.mordorx.flacblaster.fs

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

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

    @Query("SELECT COUNT(*) FROM files")
    fun getFileEntityCount(): Int

    @Delete
    fun delete(vararg files: FileEntity)
}