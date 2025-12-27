package xyz.mordorx.flacblaster.fs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FileEntity::class], exportSchema = false, version = 1)
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
                    )
                    .enableMultiInstanceInvalidation()
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}