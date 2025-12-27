package xyz.mordorx.flacblaster.fs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.io.File

@Entity(tableName = "files")
@TypeConverters(Converters::class)
data class FileEntity(
    /** M1 for files and folders. This is absolute. */
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    /** M1 for files and folders */
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
    @ColumnInfo(name = "sampleRateHz") var sampleRateHz: Int,
    /** M3 for files only. This is either the average or nominal bitrate */
    @ColumnInfo(name = "bitrateKbps") var bitrateKbps: Int,
    /** M3 for files only */
    @ColumnInfo(name = "channelCount") var channelCount: Int,
    /** M3 for files only. */
    @ColumnInfo(name = "metadata") var metadata: Map<String, List<String>>,
) {
    init {
        require(path.isNotEmpty()) { "Files must have a path" }
        require(metadata.keys.all{ it.uppercase() == it }) { "Metadata keys must be stored in uppercase" }
    }

    companion object {
        /** This creates an empty FileEntity with only "path" and "isFolder" set. */
        fun emptyOfFile(f: File): FileEntity {
            return FileEntity(
                path = f.absolutePath,
                isFolder = f.isDirectory,
                lastModifiedMs = 0,
                size = 0,
                childCount = 0,
                durationMs = 0,
                metadata = mapOf(),
                bitrateKbps = 0,
                sampleRateHz = 0,
                channelCount = 0
            )
        }
    }

    /** Returns the topmost value */
    fun getName(): String {
        return path.removeSuffix("/").split('/').last()
    }
}