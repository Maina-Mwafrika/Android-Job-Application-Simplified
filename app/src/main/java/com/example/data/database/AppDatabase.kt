package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.entity.AppliedJobLog
import com.example.data.entity.ScrapedJob
import com.example.data.entity.UserCv

@Database(
    entities = [UserCv::class, ScrapedJob::class, AppliedJobLog::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cvDao(): CvDao
    abstract fun jobDao(): JobDao
    abstract fun appliedLogDao(): AppliedLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jobcraft_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
