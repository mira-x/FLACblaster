package eu.mordorx.flacblaster.fs

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.io.File
import androidx.core.net.toUri
import org.jetbrains.annotations.Debug

@Entity(tableName = "files", indices = [
    Index(value=["path"]),
    Index(value=["isFolder"]),
    Index(value=["isFolder", "path"]),
    Index(value=["isSelected", "path"]),
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

    /** User-defined for files/folders */
    @ColumnInfo(name = "isPodcast") var isPodcast: Boolean,
    /** Only used for podcasts */
    @ColumnInfo(name = "lastResumeMs") var lastResumeMs: Long,

    @ColumnInfo(name = "isSelected") var isSelected: Boolean
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
                channelCount = 0,
                isPodcast = false,
                lastResumeMs = 0,
                isSelected = false
            )
        }

        /**
         * This creates an empty, dummy FileEntity with only "path" and "isFolder" set. It is meant for @Preview functions.
         *
         * Why a separate function? Simple: Using `emptyOfFile(File("/myDir/"))` will return a `FileEntity` with `isFolder = false` because `File` drops the trailing slash. This forbids usage of folders in @Preview functions.
         */
        fun emptyOfDummy(path: String): FileEntity {
            return FileEntity(
                path = path,
                isFolder = path.endsWith('/'),
                lastModifiedMs = 0,
                size = 0,
                childCount = 0,
                durationMs = 0,
                metadata = mapOf(),
                bitrateKbps = 0,
                sampleRateHz = 0,
                channelCount = 0,
                isPodcast = false,
                lastResumeMs = 0,
                isSelected = false
            )
        }
    }

    /** Returns the topmost value */
    fun getName(): String {
        return path.removeSuffix("/").split('/').last()
    }

    /** Returns a string in the format H:MM:SS */
    fun durationString(): String {
        val secMs = 1000
        val minuteMs = secMs * 60
        val hourMs = minuteMs * 60

        val secPart = (durationMs % minuteMs) / secMs
        val minutePart = (durationMs % hourMs) / minuteMs
        val hourPart = durationMs / hourMs

        return if (hourPart > 0) {
            "%d:%02d:%02d".format(hourPart, minutePart, secPart)
        } else {
            "%02d:%02d".format(minutePart, secPart)
        }
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