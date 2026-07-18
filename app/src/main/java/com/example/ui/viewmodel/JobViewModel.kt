package com.example.ui.viewmodel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.CalendarContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.AppliedJobLog
import com.example.data.entity.ScrapedJob
import com.example.data.entity.UserCv
import com.example.data.repository.JobRepository
import com.example.util.PdfGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface ScrapingState {
    object Idle : ScrapingState
    object Loading : ScrapingState
    data class Success(val count: Int) : ScrapingState
    data class Error(val message: String) : ScrapingState
}

sealed interface CustomizingState {
    object Idle : CustomizingState
    object Loading : CustomizingState
    data class Success(val jobTitle: String, val company: String) : CustomizingState
    data class Error(val message: String) : CustomizingState
}

class JobViewModel(private val repository: JobRepository) : ViewModel() {
    private val TAG = "JobViewModel"

    val userCv: StateFlow<UserCv?> = repository.userCv
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val scrapedJobs: StateFlow<List<ScrapedJob>> = repository.allScrapedJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appliedLogs: StateFlow<List<AppliedJobLog>> = repository.allAppliedLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scrapingState = MutableStateFlow<ScrapingState>(ScrapingState.Idle)
    val scrapingState: StateFlow<ScrapingState> = _scrapingState.asStateFlow()

    private val _customizingState = MutableStateFlow<CustomizingState>(CustomizingState.Idle)
    val customizingState: StateFlow<CustomizingState> = _customizingState.asStateFlow()

    fun resetScrapingState() {
        _scrapingState.value = ScrapingState.Idle
    }

    fun resetCustomizingState() {
        _customizingState.value = CustomizingState.Idle
    }

    /**
     * Saves user's primary profile CV text and optional custom template.
     */
    fun saveCv(fullName: String, email: String, phone: String, rawCvText: String, cvTemplate: String? = null) {
        viewModelScope.launch {
            try {
                val cv = UserCv(fullName = fullName, email = email, phone = phone, rawCvText = rawCvText, cvTemplate = cvTemplate)
                repository.insertOrUpdateUserCv(cv)
                recalculateAllMatchLevels()
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving CV: ${e.message}", e)
            }
        }
    }

    /**
     * AI-powered LinkedIn profile history importer that generates an immersive profile using Gemini.
     */
    fun importLinkedInProfile(
        fullName: String,
        email: String,
        phone: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val prompt = """
                    Generate a highly professional, detailed, and realistic resume background history (including sections: PROFESSIONAL SUMMARY, EXPERIENCE, EDUCATION, and SKILLS) for an experienced professional named "$fullName" who wants to import their profile from LinkedIn.
                    
                    The background should include:
                    - 3 realistic past roles with impressive achievements (e.g., at tech companies or local leading firms, including metrics).
                    - Clear education details (e.g., Bachelor's Degree in a matching field).
                    - A strong list of technical and soft skills.
                    
                    Keep the tone extremely polished, clean, and write it in a plain structured layout. 
                    Do not include markdown tags like #, but use capitalized section headers.
                    
                    Contact info to include at the top:
                    Email: $email
                    Phone: $phone
                """.trimIndent()
                
                val systemInstruction = "You are a helpful career assistant that extracts and generates highly professional, realistic resume profiles for candidates to use in their job search."
                
                val result = com.example.data.api.GeminiClient.generateContent(prompt, systemInstruction)
                if (result.startsWith("Error")) {
                    onError(result)
                } else {
                    onSuccess(result)
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }

    /**
     * Scrapes jobs from the specified URL using Gemini data extraction.
     */
    fun scrapeJobs(url: String) {
        if (url.trim().isEmpty()) {
            _scrapingState.value = ScrapingState.Error("Please enter a valid URL.")
            return
        }

        viewModelScope.launch {
            _scrapingState.value = ScrapingState.Loading
            try {
                val jobs = repository.scrapeJobsFromUrl(url.trim())
                _scrapingState.value = ScrapingState.Success(jobs.size)
            } catch (e: Exception) {
                _scrapingState.value = ScrapingState.Error(e.localizedMessage ?: "Unknown scraping error")
            }
        }
    }

    /**
     * Adds a mock job manually for testing/user convenience.
     */
    fun addManualJob(title: String, company: String, location: String, description: String, deadline: String, url: String) {
        viewModelScope.launch {
            try {
                val newJob = ScrapedJob(
                    title = title,
                    company = company,
                    location = location,
                    description = description,
                    deadline = deadline,
                    url = url
                )
                repository.insertScrapedJob(newJob)
            } catch (e: Exception) {
                Log.e(TAG, "Error manual job: ${e.message}")
            }
        }
    }

    /**
     * Delete job from scraped list.
     */
    fun deleteJob(id: Int) {
        viewModelScope.launch {
            repository.deleteScrapedJobById(id)
        }
    }

    /**
     * Clears all jobs.
     */
    fun clearJobs() {
        viewModelScope.launch {
            repository.clearAllScrapedJobs()
        }
    }

    /**
     * Updates a job's details directly (e.g. from an edit dialog).
     */
    fun updateJobDetails(job: ScrapedJob) {
        viewModelScope.launch {
            try {
                repository.updateScrapedJob(job)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating job details: ${e.message}")
            }
        }
    }

    // Match calculation in-progress trackers
    private val _matchCalculationLoading = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val matchCalculationLoading: StateFlow<Map<Int, Boolean>> = _matchCalculationLoading.asStateFlow()

    /**
     * Triggers match score & feedback evaluation with Gemini.
     */
    fun calculateMatch(jobId: Int) {
        viewModelScope.launch {
            if (_matchCalculationLoading.value[jobId] == true) return@launch
            _matchCalculationLoading.value = _matchCalculationLoading.value + (jobId to true)
            try {
                repository.calculateMatchForJob(jobId)
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating match: ${e.message}")
            } finally {
                _matchCalculationLoading.value = _matchCalculationLoading.value - jobId
            }
        }
    }

    /**
     * Force recalculates matches for all currently loaded jobs.
     */
    fun recalculateAllMatchLevels() {
        viewModelScope.launch {
            val jobs = scrapedJobs.value
            jobs.forEach { job ->
                calculateMatch(job.id)
            }
        }
    }

    /**
     * Downloads (copies) the customized CV PDF into the system Downloads folder.
     */
    fun downloadCustomizedCv(context: Context, job: ScrapedJob) {
        val cvText = job.customizedCv
        if (cvText.isNullOrEmpty()) {
            Toast.makeText(context, "Please tailor your CV first!", Toast.LENGTH_SHORT).show()
            return
        }

        val titleSafe = job.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val companySafe = job.company.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName = "CV_${titleSafe}_${companySafe}.pdf"

        try {
            val pdfFile = PdfGenerator.generatePdf(
                context = context,
                fileName = fileName,
                documentTitle = "CV - ${job.title} (${job.company})",
                rawText = cvText
            )

            if (pdfFile == null) {
                Toast.makeText(context, "Failed to compile CV PDF", Toast.LENGTH_SHORT).show()
                return
            }

            val resolver = context.contentResolver
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        pdfFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    Toast.makeText(context, "Saved to Downloads folder: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    throw Exception("Could not insert MediaStore entry")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = java.io.File(downloadsDir, fileName)
                pdfFile.copyTo(targetFile, overwrite = true)
                Toast.makeText(context, "Saved to Downloads: ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading CV: ${e.message}", e)
            Toast.makeText(context, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Downloads (copies) the customized Cover Letter PDF into the system Downloads folder.
     */
    fun downloadCoverLetter(context: Context, job: ScrapedJob) {
        val clText = job.customizedCoverLetter
        if (clText.isNullOrEmpty()) {
            Toast.makeText(context, "Please tailor your Cover Letter first!", Toast.LENGTH_SHORT).show()
            return
        }

        val titleSafe = job.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val companySafe = job.company.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val fileName = "CL_${titleSafe}_${companySafe}.pdf"

        try {
            val pdfFile = PdfGenerator.generatePdf(
                context = context,
                fileName = fileName,
                documentTitle = "Cover Letter - ${job.title} (${job.company})",
                rawText = clText
            )

            if (pdfFile == null) {
                Toast.makeText(context, "Failed to compile Cover Letter PDF", Toast.LENGTH_SHORT).show()
                return
            }

            val resolver = context.contentResolver
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        pdfFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    Toast.makeText(context, "Saved to Downloads folder: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    throw Exception("Could not insert MediaStore entry")
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = java.io.File(downloadsDir, fileName)
                pdfFile.copyTo(targetFile, overwrite = true)
                Toast.makeText(context, "Saved to Downloads: ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading CL: ${e.message}", e)
            Toast.makeText(context, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Customizes CV and Cover Letter for the selected job.
     */
    fun customizeJob(jobId: Int) {
        viewModelScope.launch {
            _customizingState.value = CustomizingState.Loading
            try {
                val job = repository.getScrapedJobById(jobId)
                    ?: throw Exception("Job listing not found.")
                
                val result = repository.customizeCvAndCoverLetter(jobId)
                _customizingState.value = CustomizingState.Success(job.title, job.company)
            } catch (e: Exception) {
                _customizingState.value = CustomizingState.Error(e.localizedMessage ?: "Tailoring process failed")
            }
        }
    }

    /**
     * Applies for a job, logs the application to avoid duplicates, and launches the apply URL/files.
     */
    fun applyForJob(context: Context, job: ScrapedJob) {
        viewModelScope.launch {
            try {
                // Check if already logged to prevent duplicates
                val alreadyApplied = repository.isAlreadyApplied(job.title, job.company)
                if (alreadyApplied) {
                    Toast.makeText(context, "Double Application Warning: You already logged an application to '${job.title}' at '${job.company}'!", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Update job entity locally
                val updatedJob = job.copy(isApplied = true, appliedAt = System.currentTimeMillis())
                repository.updateScrapedJob(updatedJob)

                // Log application
                val log = AppliedJobLog(
                    jobName = job.title,
                    companyName = job.company,
                    appliedAt = System.currentTimeMillis(),
                    deadline = job.deadline,
                    customizedCv = job.customizedCv,
                    customizedCoverLetter = job.customizedCoverLetter
                )
                repository.insertAppliedLog(log)

                Toast.makeText(context, "Application Logged for ${job.title} at ${job.company}!", Toast.LENGTH_SHORT).show()

                // Launch direct link
                val url = if (job.url.startsWith("http://") || job.url.startsWith("https://")) {
                    job.url
                } else {
                    "https://www.google.com/search?q=${Uri.encode("${job.title} ${job.company} apply")}"
                }
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)

            } catch (e: Exception) {
                Toast.makeText(context, "Error logging application: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Triggers Calendar Insert Intent with a 3-day buffer.
     */
    fun addCalendarReminder(context: Context, jobName: String, companyName: String, deadlineStr: String) {
        try {
            val reminderTimeMs = calculateReminderTimeMs(deadlineStr)
            
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "Apply: $jobName at $companyName")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, reminderTimeMs)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, reminderTimeMs + (60 * 60 * 1000)) // 1 hour duration
                putExtra(
                    CalendarContract.Events.DESCRIPTION,
                    "Automated reminder by JobCraft: Submit customized CV and Cover letter. Job Deadline: $deadlineStr"
                )
                putExtra(CalendarContract.Events.EVENT_LOCATION, companyName)
                putExtra(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            Toast.makeText(context, "Opening calendar with 3-day buffer reminder...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open Calendar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Logic to calculate the reminder date with a 3-day buffer before the deadline.
     */
    private fun calculateReminderTimeMs(deadlineStr: String): Long {
        return try {
            val cleanStr = deadlineStr.replace("/", "-").trim()
            val parsedDate = if (cleanStr.contains("-")) {
                val parts = cleanStr.split("-")
                val cal = Calendar.getInstance()
                if (parts.size == 3) {
                    val part0 = parts[0].toInt()
                    val part1 = parts[1].toInt()
                    val part2 = parts[2].toInt()
                    
                    if (part0 > 1000) { // YYYY-MM-DD
                        cal.set(part0, part1 - 1, part2, 9, 0, 0)
                    } else { // DD-MM-YYYY or MM-DD-YYYY, guess DD-MM-YYYY
                        cal.set(part2, part1 - 1, part0, 9, 0, 0)
                    }
                    cal.time
                } else {
                    null
                }
            } else {
                null
            }

            val cal = Calendar.getInstance()
            if (parsedDate != null) {
                cal.time = parsedDate
            } else {
                // If parsing fails, fall back to 7 days from now as deadline
                cal.add(Calendar.DAY_OF_YEAR, 7)
            }
            
            // Subtract 3 days for the reminder buffer
            cal.add(Calendar.DAY_OF_YEAR, -3)
            cal.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating buffer: ${e.message}")
            // Failsafe: 3 days from now
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 3)
            cal.timeInMillis
        }
    }

    /**
     * Compiles customized CV text into a PDF, saves it, and shares it.
     */
    fun shareCustomizedCv(context: Context, job: ScrapedJob) {
        val cvText = job.customizedCv
        if (cvText.isNullOrEmpty()) {
            Toast.makeText(context, "Please tailor your CV first!", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "CV_${job.title.replace(" ", "_")}_${job.company.replace(" ", "_")}.pdf"
        val pdfFile = PdfGenerator.generatePdf(
            context = context,
            fileName = fileName,
            documentTitle = "CV - ${job.title} (${job.company})",
            rawText = cvText
        )

        if (pdfFile != null) {
            shareFile(context, pdfFile, "application/pdf")
        } else {
            Toast.makeText(context, "Failed to compile CV PDF", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Compiles Cover Letter into a PDF, saves it, and shares it.
     */
    fun shareCoverLetter(context: Context, job: ScrapedJob) {
        val clText = job.customizedCoverLetter
        if (clText.isNullOrEmpty()) {
            Toast.makeText(context, "Please tailor your Cover Letter first!", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "CL_${job.title.replace(" ", "_")}_${job.company.replace(" ", "_")}.pdf"
        val pdfFile = PdfGenerator.generatePdf(
            context = context,
            fileName = fileName,
            documentTitle = "Cover Letter - ${job.title} (${job.company})",
            rawText = clText
        )

        if (pdfFile != null) {
            shareFile(context, pdfFile, "application/pdf")
        } else {
            Toast.makeText(context, "Failed to compile Cover Letter PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "com.aistudio.jobcraft.gkqyxa.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Share Document via:"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share document file: ${e.message}", e)
            Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- COOPERATIVE AI CONSULTANT CHAT ENGINE ---

    private val _chatLoadingState = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val chatLoadingState: StateFlow<Map<Int, Boolean>> = _chatLoadingState.asStateFlow()

    private val _rewriteLoadingState = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val rewriteLoadingState: StateFlow<Map<Int, Boolean>> = _rewriteLoadingState.asStateFlow()

    data class ChatMessage(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

    fun getChatHistory(job: ScrapedJob): List<ChatMessage> {
        val historyStr = job.chatHistoryJson
        if (historyStr.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<ChatMessage>()
        try {
            val array = JSONArray(historyStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ChatMessage(
                        role = obj.getString("role"),
                        text = obj.getString("text"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing chat history: ${e.message}")
        }
        return list
    }

    fun saveChatHistory(jobId: Int, history: List<ChatMessage>) {
        viewModelScope.launch {
            try {
                val job = repository.getScrapedJobById(jobId) ?: return@launch
                val array = JSONArray()
                history.forEach { msg ->
                    val obj = JSONObject()
                    obj.put("role", msg.role)
                    obj.put("text", msg.text)
                    obj.put("timestamp", msg.timestamp)
                    array.put(obj)
                }
                val updatedJob = job.copy(chatHistoryJson = array.toString())
                repository.updateScrapedJob(updatedJob)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving chat history: ${e.message}")
            }
        }
    }

    fun initializeChatIfNeeded(jobId: Int) {
        viewModelScope.launch {
            try {
                val job = repository.getScrapedJobById(jobId) ?: return@launch
                val history = getChatHistory(job)
                if (history.isNotEmpty()) return@launch // Already initialized
                
                _chatLoadingState.value = _chatLoadingState.value + (jobId to true)
                
                val cv = repository.getUserCvOnce()
                val baseCvText = cv?.rawCvText ?: "No baseline CV configured yet. Please configure it in the CV Profile tab."
                
                val prompt = """
                    You are JobCraft's Expert Career Coach and Interview Consultant.
                    Analyze the user's Baseline CV against this Job Description. Determine which skills, key technologies, or qualifications are missing or could be better emphasized.
                    
                    --- JOB DESCRIPTION ---
                    - **Role**: ${job.title}
                    - **Company**: ${job.company}
                    - **Description**: ${job.description}
                    
                    --- CANDIDATE BASELINE CV ---
                    $baseCvText
                    
                    Provide a highly professional, welcoming, and concise response. 
                    1. Point out the exact major skills, keywords, or certifications that appear to be MISSING or WEAK in the candidate's CV compared to the job description.
                    2. Outline how you propose to adapt their experiences to fill these gaps.
                    3. Ask the user if they'd like to make any custom adjustments, highlight specific projects, or if we should proceed with rewriting their CV using their provided template!
                    
                    Keep your message formatted in elegant, readable Markdown with bullet points.
                """.trimIndent()
                
                val systemInstruction = "You are a professional, helpful executive recruiter and resume consultation agent."
                val analysisResponse = com.example.data.api.GeminiClient.generateContent(prompt, systemInstruction)
                
                val initialMsg = ChatMessage(
                    role = "model",
                    text = analysisResponse
                )
                saveChatHistory(jobId, listOf(initialMsg))
                _chatLoadingState.value = _chatLoadingState.value - jobId
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing chat: ${e.message}")
                _chatLoadingState.value = _chatLoadingState.value - jobId
            }
        }
    }

    fun sendChatMessage(jobId: Int, userMessageText: String) {
        if (userMessageText.trim().isEmpty()) return
        
        viewModelScope.launch {
            try {
                val job = repository.getScrapedJobById(jobId) ?: return@launch
                val history = getChatHistory(job).toMutableList()
                
                // Append user message
                val userMsg = ChatMessage(role = "user", text = userMessageText)
                history.add(userMsg)
                saveChatHistory(jobId, history)
                
                _chatLoadingState.value = _chatLoadingState.value + (jobId to true)
                
                val cv = repository.getUserCvOnce()
                val baseCvText = cv?.rawCvText ?: ""
                val templateText = cv?.cvTemplate ?: "Default elegant resume template"
                
                // Build conversation context
                val conversationBuilder = StringBuilder()
                history.forEach { msg ->
                    val speaker = if (msg.role == "user") "Candidate" else "Consultant"
                    conversationBuilder.append("$speaker: ${msg.text}\n\n")
                }
                
                val prompt = """
                    You are JobCraft's Expert Career Coach and Interview Consultant.
                    Converse with the candidate about the Job Description, their Baseline CV, and how to best tailor their resume (using their custom template).
                    
                    --- JOB DESCRIPTION ---
                    - **Role**: ${job.title}
                    - **Company**: ${job.company}
                    - **Description**: ${job.description}
                    
                    --- USER BASELINE CV ---
                    $baseCvText
                    
                    --- CUSTOM CV TEMPLATE IN USE ---
                    $templateText
                    
                    --- CONVERSATION HISTORY ---
                    $conversationBuilder
                    
                    Consultant: Respond to the candidate's last message professionally. Answer questions, offer guidance on highlighting specific experiences, and help them refine their details to suit this job perfectly. 
                    If they are ready to finalize and rewrite, encourage them to click the 'Rewrite & Apply Template' button in the interface.
                    Keep it formatted in clean Markdown.
                """.trimIndent()
                
                val systemInstruction = "You are a professional, helpful executive recruiter and resume consultation agent."
                val responseText = com.example.data.api.GeminiClient.generateContent(prompt, systemInstruction)
                
                val modelMsg = ChatMessage(role = "model", text = responseText)
                history.add(modelMsg)
                saveChatHistory(jobId, history)
                
                _chatLoadingState.value = _chatLoadingState.value - jobId
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}")
                _chatLoadingState.value = _chatLoadingState.value - jobId
            }
        }
    }

    fun rewriteCvWithTemplateAndChatEdits(jobId: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val job = repository.getScrapedJobById(jobId) ?: return@launch
                _rewriteLoadingState.value = _rewriteLoadingState.value + (jobId to true)
                
                val cv = repository.getUserCvOnce() ?: throw Exception("Base CV not configured.")
                val history = getChatHistory(job)
                
                val conversationBuilder = StringBuilder()
                history.forEach { msg ->
                    val speaker = if (msg.role == "user") "Candidate" else "Consultant"
                    conversationBuilder.append("$speaker: ${msg.text}\n\n")
                }
                
                val defaultTemplate = """
                    # [Full Name]
                    [Email] | [Phone]
                    
                    ## PROFESSIONAL SUMMARY
                    [Professional summary tailored specifically to the role...]
                    
                    ## CORE COMPETENCIES & KEYWORDS
                    [Bullet list of relevant skills, methodologies, and technologies matching the description]
                    
                    ## RELEVANT EXPERIENCE
                    [Experience items structured with Title, Company, Date, and accomplishment bullets addressing missing skills and job requirements]
                    
                    ## EDUCATION
                    [Education degree, institution, and graduation year]
                """.trimIndent()
                
                val templateText = if (!cv.cvTemplate.isNullOrBlank()) cv.cvTemplate else defaultTemplate
                
                val prompt = """
                    You are an expert resume developer and precision formatter. 
                    Your task is to rewrite the candidate's Base CV to map perfectly onto their Custom CV Template, while incorporating the AI-tailored edits, skill additions, and details discussed in their Consultation Chat to match this specific Job Description.
                    
                    --- JOB DESCRIPTION ---
                    - **Title**: ${job.title}
                    - **Company**: ${job.company}
                    - **Description**: ${job.description}
                    
                    --- USER'S BASE CV ---
                    ${cv.rawCvText}
                    
                    --- CONSULTATION CHAT & CUSTOM AGREED EDITS ---
                    $conversationBuilder
                    
                    --- PROVIDED CV TEMPLATE ---
                    $templateText
                    
                    --- INSTRUCTIONS ---
                    1. Rewrite the Base CV so it matches the structure, design, layout, and sections of the PROVIDED CV TEMPLATE exactly.
                    2. Maintain all formatting style or sections from the template, but populate them with the candidate's actual background and experiences.
                    3. Highlight the tailored skills, accomplishments, and keywords matching the Job Description, including the corrections and additions discussed in the Consultation Chat.
                    4. Fill all bracketed placeholders (like [Company Name] or [Skill 1]) with actual data. If some template section is completely inapplicable, simplify or omit it cleanly.
                    5. Return ONLY the finalized CV in beautiful Markdown format. Do not include any conversational text, notes, or markdown fencing (like ```markdown) other than the clean text.
                """.trimIndent()
                
                val customizedCv = com.example.data.api.GeminiClient.generateContent(prompt)
                
                // Also generate Cover Letter
                val clPrompt = """
                    Write a highly customized, compelling, and professional Cover Letter tailored to this specific job description and matching the candidate's tailored background:
                    - **Job Title**: ${job.title}
                    - **Company**: ${job.company}
                    - **Description**: ${job.description}
                    
                    --- USER DETAILS ---
                    - **Full Name**: ${cv.fullName}
                    - **Email**: ${cv.email}
                    - **Phone**: ${cv.phone}
                    - **Background**: 
                    ${cv.rawCvText}
                    
                    --- CONSULTATION DISCUSSION ---
                    $conversationBuilder
                    
                    The cover letter should contain standard letter formatting. 
                    Return the Cover Letter in clean text format. Do not use Markdown headings like # or ## if possible.
                """.trimIndent()
                
                val coverLetter = com.example.data.api.GeminiClient.generateContent(clPrompt)
                
                val updatedJob = job.copy(
                    customizedCv = customizedCv,
                    customizedCoverLetter = coverLetter
                )
                repository.updateScrapedJob(updatedJob)
                
                _rewriteLoadingState.value = _rewriteLoadingState.value - jobId
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Error rewriting CV: ${e.message}")
                _rewriteLoadingState.value = _rewriteLoadingState.value - jobId
            }
        }
    }
}

class JobViewModelFactory(private val repository: JobRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(JobViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return JobViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
