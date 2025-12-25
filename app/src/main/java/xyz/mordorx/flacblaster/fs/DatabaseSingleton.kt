package xyz.mordorx.flacblaster.fs

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.io.File

@Database(entities = [FileEntity::class], exportSchema = false, version = 1)
@TypeConverters(Converters::class)
abstract class DatabaseSingleton : RoomDatabase() {
    abstract fun fileEntityDao(): FileEntityDao

    companion object {
        @Volatile
        private var INSTANCE: DatabaseSingleton? = null

        fun get(context: Context): DatabaseSingleton {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    DatabaseSingleton::class.java,
                    "files.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}