package xyz.mordorx.flacblaster.fs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File
import java.util.HashMap

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "isFolder") val isFolder: Boolean,
    /** M1 for files and folders */
    @ColumnInfo(name = "lastModifiedMs") var lastModifiedMs: Long,
    /** M1 for files, M2 for folders */
    @ColumnInfo(name = "size") var size: Long,
    /** M2 data that only applies to folders */
    @ColumnInfo(name = "childCount") var childCount: Int,
    /** M3 for files and folders */
    @ColumnInfo(name = "durationMs") var durationMs: Int,
    /** M3 for files only */
    @ColumnInfo(name = "metadata") var metadata: Map<String, List<String>>
) {
    init {
        require(path.isNotEmpty()) { "Files must have a bath" }
    }

    companion object {
        /** Generates a FileEntity of the given file (or folder) with M1 metadata inserted. This is not written to the DB. */
        fun m1OfFile(f: File): FileEntity {
            return FileEntity(
                path = f.absolutePath,
                isFolder = f.isDirectory,
                lastModifiedMs = f.lastModified(),
                size = when (f.isFile) {
                    true -> f.length()
                    false -> -1
                },
                childCount = -1,
                durationMs = -1,
                metadata = HashMap()
            )
        }
    }
}