package eu.mordorx.flacblaster.fs

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.io.File
import androidx.core.net.toUri

@Entity(tableName = "files", indices = [
    Index(value=["path"]),
    Index(value=["isFolder"]),
    Index(value=["isFolder", "path"]),
])
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

    /** Returns a string in the format D:HH:MM:SS, leaving out the Days and Hours fields if possible */
    fun durationString(): String {
        val secMs = 1000
        val minuteMs = secMs * 60
        val hourMs = minuteMs * 60
        val dayMs = hourMs * 24

        val secPart = (durationMs % minuteMs) / secMs
        val minutePart = (durationMs % hourMs) / minuteMs
        val hourPart = (durationMs % dayMs) / hourMs
        val dayPart = durationMs / dayMs

        var out = ""
        if (dayPart > 0) out += "$dayPart:"
        if (hourPart > 0) out += "$hourPart".padStart(if (dayPart > 0) 2 else 1, '0') + ":"
        out += minutePart.toString().padStart(2, '0')
        out += ":"
        out += secPart.toString().padStart(2, '0')
        return out
    }

    fun isChildOf(parentFolder: FileEntity): Boolean {
        if (!path.startsWith(parentFolder.path)) {
            return false
        }
        val pathWithout = path.removePrefix(parentFolder.path + "/")
        if (pathWithout.count{it == '/'} > 0) {
            return false
        } else {
            return true
        }
    }

    fun getUri(): Uri {
        return "file://$path".toUri()
    }
}