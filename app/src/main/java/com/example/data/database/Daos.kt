package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.AppliedJobLog
import com.example.data.entity.ScrapedJob
import com.example.data.entity.UserCv
import kotlinx.coroutines.flow.Flow

@Dao
interface CvDao {
    @Query("SELECT * FROM user_cv WHERE id = 1")
    fun getUserCv(): Flow<UserCv?>

    @Query("SELECT * FROM user_cv WHERE id = 1")
    suspend fun getUserCvOnce(): UserCv?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserCv(cv: UserCv)
}

@Dao
interface JobDao {
    @Query("SELECT * FROM scraped_jobs ORDER BY id DESC")
    fun getAllScrapedJobs(): Flow<List<ScrapedJob>>

    @Query("SELECT * FROM scraped_jobs WHERE id = :id")
    suspend fun getScrapedJobById(id: Int): ScrapedJob?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScrapedJob(job: ScrapedJob): Long

    @Update
    suspend fun updateScrapedJob(job: ScrapedJob)

    @Query("DELETE FROM scraped_jobs WHERE id = :id")
    suspend fun deleteScrapedJobById(id: Int)

    @Query("DELETE FROM scraped_jobs")
    suspend fun clearAllScrapedJobs()
}

@Dao
interface AppliedLogDao {
    @Query("SELECT * FROM applied_job_logs ORDER BY appliedAt DESC")
    fun getAllAppliedLogs(): Flow<List<AppliedJobLog>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAppliedLog(log: AppliedJobLog): Long

    @Query("SELECT EXISTS(SELECT 1 FROM applied_job_logs WHERE UPPER(jobName) = UPPER(:jobName) AND UPPER(companyName) = UPPER(:companyName))")
    suspend fun isAlreadyApplied(jobName: String, companyName: String): Boolean
}
