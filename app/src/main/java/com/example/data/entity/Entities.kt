package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_cv")
data class UserCv(
    @PrimaryKey val id: Int = 1,
    val fullName: String,
    val email: String,
    val phone: String,
    val rawCvText: String,
    val cvTemplate: String? = null
)

@Entity(tableName = "scraped_jobs")
data class ScrapedJob(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val company: String,
    val location: String,
    val description: String,
    val deadline: String, // format YYYY-MM-DD
    val url: String,
    val customizedCv: String? = null,
    val customizedCoverLetter: String? = null,
    val isApplied: Boolean = false,
    val appliedAt: Long? = null,
    val chatHistoryJson: String? = null,
    val matchScore: Int? = null,
    val matchFeedback: String? = null,
    val industry: String = "Other / General",
    // NEW FIELD: true when this listing's data (and especially its `url`) was AI-generated or
    // heuristically guessed rather than confirmed real -- e.g. the source site couldn't be reached,
    // or the apply link failed a reachability check. The UI should badge these clearly so users
    // never mistake a fabricated posting for a verified one.
    val isSimulated: Boolean = false
)

@Entity(
    tableName = "applied_job_logs",
    indices = [Index(value = ["jobName", "companyName"], unique = true)]
)
data class AppliedJobLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val jobName: String,
    val companyName: String,
    val appliedAt: Long,
    val deadline: String,
    val customizedCv: String? = null,
    val customizedCoverLetter: String? = null,
    val status: String = "Applied" // "Applied", "Interviewing", "Hired", "Rejected"
)