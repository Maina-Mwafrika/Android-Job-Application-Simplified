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
    // MODIFIED: version bumped 4 -> 5 because ScrapedJob gained the new `isSimulated` column.
    // fallbackToDestructiveMigration() below means existing local rows are wiped on upgrade
    // rather than migrated -- fine for this app's current stage, but note it if you ship an
    // update to real users and want to preserve their scraped listings / applied logs.
    version = 5,
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