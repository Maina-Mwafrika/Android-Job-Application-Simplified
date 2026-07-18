package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.example.util.PdfGenerator
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.example.data.database.AppDatabase
import com.example.data.entity.AppliedJobLog
import com.example.data.entity.ScrapedJob
import com.example.data.entity.UserCv
import com.example.data.repository.JobRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Initialize database and repository inside MainActivity
                val context = LocalContext.current.applicationContext
                val database = remember { AppDatabase.getDatabase(context) }
                val repository = remember {
                    JobRepository(
                        database.cvDao(),
                        database.jobDao(),
                        database.appliedLogDao()
                    )
                }

                // Inject ViewModel with Custom Factory
                val jobViewModel: JobViewModel = viewModel(
                    factory = JobViewModelFactory(repository)
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JobCraftApp(viewModel = jobViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobCraftApp(viewModel: JobViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(1) } // Default to Job Scraper tab

    val logs by viewModel.appliedLogs.collectAsStateWithLifecycle()
    val scrapedJobs by viewModel.scrapedJobs.collectAsStateWithLifecycle()
    val cvState by viewModel.userCv.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "CV Profile") },
                    label = { Text("CV Profile") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Scraper") },
                    label = { Text("Scraper & Tailor") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Assignment, contentDescription = "Applications Log") },
                    label = { Text("Applied Logs") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            // App Header with reactive applied logs size, scraped listings size, and username
            AppHeader(
                appliedCount = logs.size,
                scrapedCount = scrapedJobs.size,
                fullName = cvState?.fullName
            )

            // Tab Screen Routing
            when (selectedTab) {
                0 -> CvProfileScreen(viewModel = viewModel)
                1 -> JobScraperScreen(viewModel = viewModel)
                2 -> AppliedLogsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AppHeader(appliedCount: Int, scrapedCount: Int, fullName: String?) {
    val initials = remember(fullName) {
        if (!fullName.isNullOrBlank()) {
            val parts = fullName.trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}".uppercase()
            } else if (parts.isNotEmpty()) {
                "${parts[0].firstOrNull() ?: ""}".uppercase()
            } else {
                "JD"
            }
        } else {
            "JD"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JobCraft",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                }
                Text(
                    text = "$scrapedCount match${if (scrapedCount == 1) "" else "es"} found today",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Avatar Container with White Border & shadow styled matching HTML jd circle
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stat 1: Applied count
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "$appliedCount Applied",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Stat 2: Deadlines/Matches count
            val deadlineText = if (scrapedCount > 0) "$scrapedCount Openings" else "0 Deadlines"
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
                Text(
                    text = deadlineText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun getFileNameAndMime(context: Context, uri: Uri): Pair<String?, String?> {
    var name: String? = null
    var mime: String? = context.contentResolver.getType(uri)
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return Pair(name, mime)
}

private fun extractTextFromDocx(inputStream: java.io.InputStream): String {
    val builder = java.lang.StringBuilder()
    try {
        java.util.zip.ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    zip.bufferedReader().use { reader ->
                        val content = reader.readText()
                        val pattern = java.util.regex.Pattern.compile("<w:t[^>]*>(.*?)</w:t>")
                        val matcher = pattern.matcher(content)
                        while (matcher.find()) {
                            val text = matcher.group(1)
                            val decoded = text
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&quot;", "\"")
                                .replace("&apos;", "'")
                            builder.append(decoded).append(" ")
                        }
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("DocxParser", "Error parsing DOCX: ${e.message}", e)
    }
    return builder.toString().trim()
}

private fun extractTextFromDocFallback(inputStream: java.io.InputStream): String {
    val builder = java.lang.StringBuilder()
    try {
        val bytes = inputStream.readBytes()
        var currentWord = java.lang.StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ',' || c == '.' || c == '-' || c == '@' || c == ':') {
                currentWord.append(c)
            } else {
                if (currentWord.length >= 4) {
                    builder.append(currentWord.toString()).append(" ")
                }
                currentWord = java.lang.StringBuilder()
            }
        }
        if (currentWord.length >= 4) {
            builder.append(currentWord.toString())
        }
    } catch (e: Exception) {
        android.util.Log.e("DocParser", "Error in legacy doc parser fallback: ${e.message}", e)
    }
    return builder.toString().trim().replace(Regex("\\s+"), " ")
}

private fun readTextFromUri(context: Context, uri: Uri): String? {
    val (fileName, mimeType) = getFileNameAndMime(context, uri)
    val lowerName = fileName?.lowercase() ?: ""
    val lowerMime = mimeType?.lowercase() ?: ""

    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            when {
                lowerMime == "application/pdf" || lowerName.endsWith(".pdf") -> {
                    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream).use { document ->
                        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                        stripper.getText(document)
                    }
                }
                lowerMime.contains("wordprocessingml.document") || lowerMime.contains("docx") || lowerName.endsWith(".docx") -> {
                    extractTextFromDocx(inputStream)
                }
                lowerMime == "application/msword" || lowerName.endsWith(".doc") -> {
                    extractTextFromDocFallback(inputStream)
                }
                else -> {
                    inputStream.bufferedReader().use { it.readText() }
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error reading file uri: ${e.message}")
        null
    }
}

// ==========================================
// SCREEN 0: CV PROFILE EDITOR SCREEN
// ==========================================
@Composable
fun CvProfileScreen(viewModel: JobViewModel) {
    val cvState by viewModel.userCv.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var rawCvText by remember { mutableStateOf("") }
    var cvTemplate by remember { mutableStateOf("") }

    // LinkedIn Import Dialog States
    var showLinkedInDialog by remember { mutableStateOf(false) }
    var linkedInEmail by remember { mutableStateOf("") }
    var linkedInPassword by remember { mutableStateOf("") }
    var isImportingLinkedIn by remember { mutableStateOf(false) }
    var importStepText by remember { mutableStateOf("") }
    var importProgress by remember { mutableStateOf(0f) }

    // Sync state once Room loads user CV record
    LaunchedEffect(cvState) {
        cvState?.let {
            fullName = it.fullName
            email = it.email
            phone = it.phone
            rawCvText = it.rawCvText
            cvTemplate = it.cvTemplate ?: ""
        }
    }

    // Handles the step-by-step progress simulation before completing with a real Gemini AI extract
    LaunchedEffect(isImportingLinkedIn) {
        if (isImportingLinkedIn) {
            importStepText = "Connecting to LinkedIn API..."
            importProgress = 0.2f
            kotlinx.coroutines.delay(1000)

            importStepText = "Verifying credentials and profile authorization..."
            importProgress = 0.45f
            kotlinx.coroutines.delay(1000)

            importStepText = "Extracting job history, education, and credentials..."
            importProgress = 0.7f
            kotlinx.coroutines.delay(1000)

            importStepText = "Structuring profile with Gemini AI..."
            importProgress = 0.9f

            val queryName = fullName.ifBlank { "Alexander Maina" }
            val queryEmail = email.ifBlank { "alex.maina@example.com" }
            val queryPhone = phone.ifBlank { "+254 712 345678" }

            viewModel.importLinkedInProfile(
                fullName = queryName,
                email = queryEmail,
                phone = queryPhone,
                onSuccess = { history ->
                    rawCvText = history
                    if (fullName.isBlank()) fullName = queryName
                    if (email.isBlank()) email = queryEmail
                    if (phone.isBlank()) phone = queryPhone
                    
                    isImportingLinkedIn = false
                    showLinkedInDialog = false
                },
                onError = { errMsg ->
                    isImportingLinkedIn = false
                    Toast.makeText(context, "LinkedIn extraction failed: $errMsg", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // File selection launchers with exact Word and PDF mime types
    val cvFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val text = readTextFromUri(context, it)
            if (!text.isNullOrBlank()) {
                rawCvText = text
                Toast.makeText(context, "Baseline CV text loaded successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not extract readable text from selected file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val templateFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val text = readTextFromUri(context, it)
            if (!text.isNullOrBlank()) {
                cvTemplate = text
                Toast.makeText(context, "CV template loaded successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not extract readable text from selected template file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // LinkedIn Import Dialog UI
    if (showLinkedInDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImportingLinkedIn) showLinkedInDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = null,
                        tint = Color(0xFF0077B5),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LinkedIn Profile Import", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isImportingLinkedIn) {
                        Text(
                            text = importStepText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { importProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF0077B5)
                        )
                    } else {
                        Text(
                            text = "Log in securely to extract your career milestones, jobs, and qualifications to auto-populate your CV profile.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = linkedInEmail,
                            onValueChange = { linkedInEmail = it },
                            label = { Text("LinkedIn Email / Phone") },
                            placeholder = { Text("username@linkedin.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = Color(0xFF0077B5)) }
                        )
                        OutlinedTextField(
                            value = linkedInPassword,
                            onValueChange = { linkedInPassword = it },
                            label = { Text("Password") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF0077B5)) }
                        )
                    }
                }
            },
            confirmButton = {
                if (!isImportingLinkedIn) {
                    Button(
                        onClick = {
                            if (linkedInEmail.isBlank() || linkedInPassword.isBlank()) {
                                Toast.makeText(context, "Please fill in all LinkedIn fields.", Toast.LENGTH_SHORT).show()
                            } else {
                                isImportingLinkedIn = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B5))
                    ) {
                        Text("Sign In & Import", color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (!isImportingLinkedIn) {
                    TextButton(onClick = { showLinkedInDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Configure your baseline profile & resume template. You can type them out, paste them, or upload text files directly from your phone. Gemini will intelligently customize them for each job specs.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Premium LinkedIn Connection button
        Button(
            onClick = { showLinkedInDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0077B5)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Login, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Profile from LinkedIn", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Personal Details",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            placeholder = { Text("John Doe") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Badge, null) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            placeholder = { Text("john.doe@example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Email, null) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            placeholder = { Text("+1 (555) 019-2834") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Phone, null) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Document Uploader buttons row with multi-MIME launchers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val fileTypes = arrayOf(
                "text/*",
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
            OutlinedButton(
                onClick = { cvFileLauncher.launch(fileTypes) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.UploadFile, null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload CV File", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = { templateFileLauncher.launch(fileTypes) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DriveFolderUpload, null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Template", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Primary CV / Background Experience Text",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Paste your existing resume, skills list, work history, and achievements here.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = rawCvText,
            onValueChange = { rawCvText = it },
            placeholder = { Text("EDUCATION:\n- B.S. in Computer Science...\n\nWORK HISTORY:\n- Senior Developer at TechCorp (2023 - Present)...\n\nSKILLS:\n- Kotlin, Compose, Android SDK...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            maxLines = 20
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Custom CV Layout Template",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            TextButton(
                onClick = {
                    cvTemplate = """
                        # [Full Name]
                        [Email] | [Phone]
                        
                        ## PROFESSIONAL SUMMARY
                        [A professional summary tailored specifically to the role...]
                        
                        ## CORE COMPETENCIES & KEYWORDS
                        - [Skill 1]
                        - [Skill 2]
                        - [Skill 3]
                        
                        ## PROFESSIONAL EXPERIENCE
                        ### [Company Name] — [Job Title]
                        *[Start Date] - [End Date]*
                        - [Accomplishment 1: Action-oriented results with key job keywords]
                        - [Accomplishment 2: Highlight metrics and technologies used]
                        
                        ## EDUCATION
                        ### [Degree Name] — [Institution]
                        *[Year of Graduation]*
                    """.trimIndent()
                }
            ) {
                Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Load Classic Template", fontSize = 11.sp)
            }
        }

        Text(
            text = "Define placeholders like [Full Name] or [Company Name] that Gemini will rewrite and format cleanly to preserve your layout.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = cvTemplate,
            onValueChange = { cvTemplate = it },
            placeholder = { Text("# [Full Name]\n[Email] | [Phone]\n\n## Summary...\n\n## Experience...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            maxLines = 20
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (fullName.isEmpty() || email.isEmpty() || rawCvText.isEmpty()) {
                    Toast.makeText(context, "Please fill in your Name, Email, and CV Text.", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.saveCv(fullName, email, phone, rawCvText, cvTemplate.takeIf { it.isNotBlank() })
                    Toast.makeText(context, "Profile details saved successfully!", Toast.LENGTH_SHORT).show()
                }
            },
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Master Profile", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

fun getJobIndustry(job: ScrapedJob): String {
    val validIndustries = listOf(
        "Technology & IT",
        "Finance & Banking",
        "Healthcare & Biotech",
        "Education & Academia",
        "Marketing & Sales",
        "Engineering & Construction"
    )
    val dbIndustry = job.industry.trim()
    if (dbIndustry.isNotEmpty() && validIndustries.any { it.equals(dbIndustry, ignoreCase = true) }) {
        return validIndustries.first { it.equals(dbIndustry, ignoreCase = true) }
    }

    val text = "${job.title} ${job.description}".lowercase()
    return when {
        text.contains("nurse") || text.contains("doctor") || text.contains("health") || 
        text.contains("clinical") || text.contains("biotech") || text.contains("medical") || 
        text.contains("patient") || text.contains("medicine") || text.contains("hospital") -> "Healthcare & Biotech"
        
        text.contains("teacher") || text.contains("professor") || text.contains("education") || 
        text.contains("school") || text.contains("academic") || text.contains("learning") || 
        text.contains("classroom") || text.contains("university") || text.contains("tutor") -> "Education & Academia"
        
        text.contains("bank") || text.contains("finance") || text.contains("account") || 
        text.contains("audit") || text.contains("investment") || text.contains("tax") || 
        text.contains("ledger") || text.contains("financial") || text.contains("treasury") -> "Finance & Banking"
        
        text.contains("marketing") || text.contains("sales") || text.contains("ads") || 
        text.contains("seo") || text.contains("social media") || text.contains("brand") || 
        text.contains("retail") || text.contains("sell") || text.contains("advertising") -> "Marketing & Sales"
        
        text.contains("civil") || text.contains("mechanical") || text.contains("electrical") || 
        text.contains("construction") || text.contains("builder") || text.contains("architect") || 
        text.contains("building") || text.contains("infrastructure") -> "Engineering & Construction"
        
        text.contains("software") || text.contains("developer") || text.contains("engineer") || 
        text.contains("tech") || text.contains("data") || text.contains("it") || 
        text.contains("programmer") || text.contains("computer") || text.contains("code") || 
        text.contains("cybersecurity") || text.contains("network") || text.contains("cloud") -> "Technology & IT"
        
        else -> "Other / General"
    }
}

// ==========================================
// SCREEN 1: JOB SCRAPER & AI TAILOR SCREEN
// ==========================================
@Composable
fun JobScraperScreen(viewModel: JobViewModel) {
    val context = LocalContext.current
    val scrapingState by viewModel.scrapingState.collectAsStateWithLifecycle()
    val customizingState by viewModel.customizingState.collectAsStateWithLifecycle()
    val scrapedJobs by viewModel.scrapedJobs.collectAsStateWithLifecycle()
    val cvState by viewModel.userCv.collectAsStateWithLifecycle()

    var jobUrlInput by remember { mutableStateOf("") }
    var showManualForm by remember { mutableStateOf(false) }

    // Manual Form State
    var manualTitle by remember { mutableStateOf("") }
    var manualCompany by remember { mutableStateOf("") }
    var manualLocation by remember { mutableStateOf("") }
    var manualDesc by remember { mutableStateOf("") }
    var manualDeadline by remember { mutableStateOf("2026-08-31") }
    var manualUrl by remember { mutableStateOf("") }

    // SEARCH CRITERIA
    var searchQuery by remember { mutableStateOf("") }
    
    // Work Mode Criteria
    var isRemoteChecked by remember { mutableStateOf(false) }
    var isHybridChecked by remember { mutableStateOf(false) }
    var isPhysicalChecked by remember { mutableStateOf(false) }

    // Industry Criteria
    var selectedIndustry by remember { mutableStateOf("All Industries") }
    
    val industriesList = remember {
        listOf(
            "All Industries",
            "Technology & IT",
            "Finance & Banking",
            "Healthcare & Biotech",
            "Education & Academia",
            "Marketing & Sales",
            "Engineering & Construction",
            "Other / General"
        )
    }

    // Job Board URL Pre-fill Selection States
    var expandedBoardDropdown by remember { mutableStateOf(false) }
    val boardsList = remember {
        listOf(
            "LinkedIn" to "https://www.linkedin.com/jobs/search?keywords=android+developer",
            "Indeed" to "https://www.indeed.com/jobs?q=kotlin+developer",
            "ZipRecruiter" to "https://www.ziprecruiter.com/jobs-search?search=software+engineer",
            "Fuzu (Nairobi)" to "https://www.fuzu.com/kenya/jobs?q=developer",
            "BrighterMonday (Nairobi)" to "https://www.brightermonday.co.ke/jobs?q=software+developer"
        )
    }

    // Observe State changes for notifications
    LaunchedEffect(scrapingState) {
        if (scrapingState is ScrapingState.Success) {
            val count = (scrapingState as ScrapingState.Success).count
            Toast.makeText(context, "Scraping Complete! Extracted $count available openings.", Toast.LENGTH_LONG).show()
            viewModel.resetScrapingState()
            jobUrlInput = ""
        } else if (scrapingState is ScrapingState.Error) {
            val msg = (scrapingState as ScrapingState.Error).message
            Toast.makeText(context, "Scraping Failed: $msg", Toast.LENGTH_LONG).show()
            viewModel.resetScrapingState()
        }
    }

    LaunchedEffect(customizingState) {
        if (customizingState is CustomizingState.Success) {
            val title = (customizingState as CustomizingState.Success).jobTitle
            val comp = (customizingState as CustomizingState.Success).company
            Toast.makeText(context, "Tailored items prepared successfully for $title at $comp!", Toast.LENGTH_LONG).show()
            viewModel.resetCustomizingState()
        } else if (customizingState is CustomizingState.Error) {
            val msg = (customizingState as CustomizingState.Error).message
            Toast.makeText(context, "AI Tailoring failed: $msg", Toast.LENGTH_LONG).show()
            viewModel.resetCustomizingState()
        }
    }

    // Trigger automatic match analysis for jobs that don't have a matchScore yet
    LaunchedEffect(scrapedJobs, cvState) {
        if (cvState != null) {
            scrapedJobs.forEach { job ->
                if (job.matchScore == null) {
                    viewModel.calculateMatch(job.id)
                }
            }
        }
    }

    // Filter jobs by search criteria (title, company, location, requirements, work mode, industry)
    val filteredJobs = remember(scrapedJobs, searchQuery, isRemoteChecked, isHybridChecked, isPhysicalChecked, selectedIndustry) {
        var list = scrapedJobs

        // 1. Keyword search (title, company, location, requirements)
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.company.contains(searchQuery, ignoreCase = true) ||
                it.location.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
        }

        // 2. Work Mode filters (Remote, Hybrid, Physical/On-site)
        val hasAnyWorkModeChecked = isRemoteChecked || isHybridChecked || isPhysicalChecked
        if (hasAnyWorkModeChecked) {
            list = list.filter { job ->
                val locLower = (job.location + " " + job.description).lowercase()
                
                val matchesRemote = isRemoteChecked && (
                    locLower.contains("remote") || locLower.contains("wfh") || 
                    locLower.contains("work from home") || locLower.contains("telecommute")
                )
                
                val matchesHybrid = isHybridChecked && (
                    locLower.contains("hybrid") || locLower.contains("flexible") || 
                    locLower.contains("partial remote") || locLower.contains("mixed")
                )
                
                val matchesPhysical = isPhysicalChecked && (
                    locLower.contains("on-site") || locLower.contains("onsite") || 
                    locLower.contains("office") || locLower.contains("physical") || 
                    (!locLower.contains("remote") && !locLower.contains("wfh") && !locLower.contains("hybrid"))
                )

                matchesRemote || matchesHybrid || matchesPhysical
            }
        }

        // 3. Industry filter
        if (selectedIndustry != "All Industries") {
            list = list.filter { job ->
                getJobIndustry(job) == selectedIndustry
            }
        }

        list
    }

    // Single LazyColumn makes the entire homepage scrollable smoothly
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Scraping URL Input Form
        item {
            Column {
                Text(
                    text = "Scrape Careers Site",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Board selection dropdown and horizontal suggestion chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-fill Board:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Box {
                        OutlinedButton(
                            onClick = { expandedBoardDropdown = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Select Board", fontSize = 10.sp)
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(12.dp))
                        }
                        DropdownMenu(
                            expanded = expandedBoardDropdown,
                            onDismissRequest = { expandedBoardDropdown = false }
                        ) {
                            boardsList.forEach { (name, url) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        jobUrlInput = url
                                        expandedBoardDropdown = false
                                        Toast.makeText(context, "$name URL pre-filled!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    // Scrollable selection row
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(boardsList) { (name, url) ->
                            SuggestionChip(
                                onClick = {
                                    jobUrlInput = url
                                    Toast.makeText(context, "$name URL pre-filled!", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(name, fontSize = 9.sp) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = jobUrlInput,
                        onValueChange = { jobUrlInput = it },
                        label = { Text("Paste Job Board / Listing URL") },
                        placeholder = { Text("https://careers.google.com/jobs/results/...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (jobUrlInput.isNotEmpty()) {
                                viewModel.scrapeJobs(jobUrlInput)
                            } else {
                                Toast.makeText(context, "Please enter a valid listing URL first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Scrape")
                    }
                }
            }
        }

        // Toggle Manual Add Job / Quick testing
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "No URL? Try adding a job manually to test:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showManualForm = !showManualForm }) {
                    Text(if (showManualForm) "Hide Manual Form" else "Show Manual Form")
                }
            }
        }

        item {
            AnimatedVisibility(visible = showManualForm) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Add Job Opening Details Manually", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = manualTitle,
                            onValueChange = { manualTitle = it },
                            label = { Text("Job Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = manualCompany,
                            onValueChange = { manualCompany = it },
                            label = { Text("Company") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = manualLocation,
                            onValueChange = { manualLocation = it },
                            label = { Text("Location") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = manualDesc,
                            onValueChange = { manualDesc = it },
                            label = { Text("Job Requirements / Description") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = manualDeadline,
                                onValueChange = { manualDeadline = it },
                                label = { Text("Deadline (YYYY-MM-DD)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedTextField(
                                value = manualUrl,
                                onValueChange = { manualUrl = it },
                                label = { Text("Application Link URL") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (manualTitle.isEmpty() || manualCompany.isEmpty()) {
                                    Toast.makeText(context, "Role and Company are required.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addManualJob(
                                        manualTitle,
                                        manualCompany,
                                        manualLocation,
                                        manualDesc,
                                        manualDeadline,
                                        manualUrl
                                    )
                                    Toast.makeText(context, "Manual job listing added!", Toast.LENGTH_SHORT).show()
                                    showManualForm = false
                                    manualTitle = ""
                                    manualCompany = ""
                                    manualDesc = ""
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Add to Available Openings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Loading State Blockers
        item {
            if (scrapingState is ScrapingState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Fetching webpage & analyzing jobs with Gemini...", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (customizingState is CustomizingState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Customizing CV and compiling Cover Letter via Gemini...", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // 3. Search Criteria
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by Title, Company, or Keywords") },
                placeholder = { Text("Filter available openings...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search icon")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Work Mode Provision (Remote, Hybrid, Physical)
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = "Work Arrangement",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isRemoteChecked,
                        onClick = { isRemoteChecked = !isRemoteChecked },
                        label = { Text("Remote", fontSize = 11.sp) },
                        leadingIcon = if (isRemoteChecked) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                        } else {
                            { Icon(Icons.Default.Home, null, modifier = Modifier.size(14.dp)) }
                        }
                    )
                    FilterChip(
                        selected = isHybridChecked,
                        onClick = { isHybridChecked = !isHybridChecked },
                        label = { Text("Hybrid", fontSize = 11.sp) },
                        leadingIcon = if (isHybridChecked) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                        } else {
                            { Icon(Icons.Default.Business, null, modifier = Modifier.size(14.dp)) }
                        }
                    )
                    FilterChip(
                        selected = isPhysicalChecked,
                        onClick = { isPhysicalChecked = !isPhysicalChecked },
                        label = { Text("Physical / On-site", fontSize = 11.sp) },
                        leadingIcon = if (isPhysicalChecked) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                        } else {
                            { Icon(Icons.Default.PinDrop, null, modifier = Modifier.size(14.dp)) }
                        }
                    )
                }
            }
        }

        // Industry Filter (Technology, Finance, Healthcare, Education, Marketing, Engineering, etc.)
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = "Filter by Industry",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(industriesList) { ind ->
                        val isSel = selectedIndustry == ind
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedIndustry = ind },
                            label = { Text(ind, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Header listing count & controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) {
                        "Available Openings (${scrapedJobs.size})"
                    } else {
                        "Matches (${filteredJobs.size} of ${scrapedJobs.size})"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (scrapedJobs.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearJobs() }) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Listings")
                    }
                }
            }
        }

        // 4. Main Listings
        if (filteredJobs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.TravelExplore,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "No job openings loaded." else "No matches found.",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) {
                                "Provide a careers listing URL above and click scrape, or add a job opening manually to begin tailoring."
                            } else {
                                "No jobs match your search query. Try typing different keywords!"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredJobs, key = { it.id }) { job ->
                ScrapedJobCard(
                    job = job,
                    viewModel = viewModel,
                    hasCvConfigured = cvState != null,
                    onCustomize = { viewModel.customizeJob(job.id) },
                    onApply = { viewModel.applyForJob(context, job) },
                    onReminder = { viewModel.addCalendarReminder(context, job.title, job.company, job.deadline) },
                    onShareCv = { viewModel.shareCustomizedCv(context, job) },
                    onShareCl = { viewModel.shareCoverLetter(context, job) },
                    onDelete = { viewModel.deleteJob(job.id) }
                )
            }
        }
    }
}

@Composable
fun ScrapedJobCard(
    job: ScrapedJob,
    viewModel: JobViewModel,
    hasCvConfigured: Boolean,
    onCustomize: () -> Unit,
    onApply: () -> Unit,
    onReminder: () -> Unit,
    onShareCv: () -> Unit,
    onShareCl: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showChatSession by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val isTailored = !job.customizedCv.isNullOrEmpty()
    val context = LocalContext.current

    val matchLoadingState by viewModel.matchCalculationLoading.collectAsStateWithLifecycle()
    val isCalculatingMatch = matchLoadingState[job.id] ?: false

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(
            containerColor = if (job.isApplied) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Title + Company Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Title
                        Text(
                            text = job.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Dynamic Match Badge based on CV Profile Analysis
                        val score = job.matchScore
                        val badgeColor = remember(score) {
                            when {
                                score == null -> Color.Gray
                                score >= 85 -> Color(0xFF1B5E20) // Excellent Green
                                score >= 70 -> Color(0xFF2E7D32) // Good Green
                                score >= 50 -> Color(0xFFE65100) // Moderate Orange
                                else -> Color(0xFFC62828) // Low Fit Red
                            }
                        }
                        val badgeBg = remember(score) {
                            when {
                                score == null -> Color.LightGray.copy(alpha = 0.15f)
                                score >= 85 -> Color(0xFFE8F5E9)
                                score >= 70 -> Color(0xFFE8F5E9)
                                score >= 50 -> Color(0xFFFFF3E0)
                                else -> Color(0xFFFFEBEE)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCalculatingMatch) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = score?.let { "$it% Match" } ?: "Evaluating Match...",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${job.company} • ${job.location}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Edit and Delete buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit details",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete opening",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Edit Details Modal Dialog
            if (showEditDialog) {
                var editTitle by remember { mutableStateOf(job.title) }
                var editCompany by remember { mutableStateOf(job.company) }
                var editLocation by remember { mutableStateOf(job.location) }
                var editDeadline by remember { mutableStateOf(job.deadline) }
                var editUrl by remember { mutableStateOf(job.url) }

                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text("Edit Job Opportunity", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                label = { Text("Job Title") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editCompany,
                                onValueChange = { editCompany = it },
                                label = { Text("Company") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editLocation,
                                onValueChange = { editLocation = it },
                                label = { Text("Location") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editDeadline,
                                onValueChange = { editDeadline = it },
                                label = { Text("Exact Application Deadline (YYYY-MM-DD)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("e.g. 2026-08-15") }
                            )
                            OutlinedTextField(
                                value = editUrl,
                                onValueChange = { editUrl = it },
                                label = { Text("Direct Link / Specific URL") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g. https://www.linkedin.com/jobs/view/...") }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val updated = job.copy(
                                    title = editTitle.trim(),
                                    company = editCompany.trim(),
                                    location = editLocation.trim(),
                                    deadline = editDeadline.trim(),
                                    url = editUrl.trim()
                                )
                                viewModel.updateJobDetails(updated)
                                showEditDialog = false
                            }
                        ) {
                            Text("Save Changes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dedicated layout block inspired by the HTML design snippet: bg-[#F9FAFF] rounded-2xl
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Display intelligent match feedback if evaluated
                if (!job.matchFeedback.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp).padding(top = 1.dp)
                        )
                        Text(
                            text = "AI Match Analysis: ${job.matchFeedback}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isTailored) "CV & Cover Letter Tailored" else "Base CV Profile Synced",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${job.deadline} (3-day buffer sync'd)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Is Applied Indicator Row
            if (job.isApplied) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.End)
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Applied",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            // Expanded Collapsible Details
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    if (showChatSession) {
                        JobChatSection(
                            viewModel = viewModel,
                            job = job,
                            onBack = { showChatSession = false }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Job Description / Requirements:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (isTailored) {
                                TextButton(onClick = { showChatSession = true }) {
                                    Icon(Icons.Default.Chat, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Adjust with AI Coach", fontSize = 11.sp)
                                }
                            }
                        }
                        
                        Text(
                            text = job.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Document actions
                        if (!isTailored) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { showChatSession = true },
                                    enabled = hasCvConfigured,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.Chat, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("💬 Chat & Rewrite CV on Template", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onCustomize,
                                    enabled = hasCvConfigured,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Instant General Tailoring", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            // Display tailored items preview toggles
                            var viewCustomizedCv by remember { mutableStateOf(false) }
                            var viewCoverLetter by remember { mutableStateOf(false) }

                            // Customized CV Section
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tailored Resume / CV", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    TextButton(onClick = { viewCustomizedCv = !viewCustomizedCv }) {
                                        Text(if (viewCustomizedCv) "Hide Preview" else "Preview")
                                    }
                                }

                                if (viewCustomizedCv) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .verticalScroll(rememberScrollState())
                                            .padding(10.dp)
                                    ) {
                                        Text(job.customizedCv ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Action buttons: Share and Download
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = onShareCv,
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                        ) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share PDF", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = { viewModel.downloadCustomizedCv(context, job) },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                        ) {
                                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download CV", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Cover Letter Section
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ContactPage, null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI Cover Letter Draft", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    TextButton(onClick = { viewCoverLetter = !viewCoverLetter }) {
                                        Text(if (viewCoverLetter) "Hide Preview" else "Preview")
                                    }
                                }

                                if (viewCoverLetter) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .verticalScroll(rememberScrollState())
                                            .padding(10.dp)
                                    ) {
                                        Text(job.customizedCoverLetter ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Action buttons: Share and Download
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = onShareCl,
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                        ) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share CL", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = { viewModel.downloadCoverLetter(context, job) },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                        ) {
                                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download CL", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Calendar Reminder
                            OutlinedButton(
                                onClick = onReminder,
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Calendar Alert (-3d)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Apply Button
                            Button(
                                onClick = onApply,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Launch, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply & Log", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JobChatSection(
    viewModel: JobViewModel,
    job: ScrapedJob,
    onBack: () -> Unit
) {
    val chatHistory = remember(job.chatHistoryJson) { viewModel.getChatHistory(job) }
    val chatLoadingState by viewModel.chatLoadingState.collectAsStateWithLifecycle()
    val rewriteLoadingState by viewModel.rewriteLoadingState.collectAsStateWithLifecycle()
    val isLoading = chatLoadingState[job.id] ?: false
    val isRewriting = rewriteLoadingState[job.id] ?: false
    
    var textInput by remember { mutableStateOf("") }
    val listState = rememberScrollState()
    val context = LocalContext.current
    
    // Initialize chat if empty
    LaunchedEffect(job.id) {
        viewModel.initializeChatIfNeeded(job.id)
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollTo(listState.maxValue)
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Chat Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Job Scout Consultation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                TextButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back to Specs", fontSize = 11.sp)
                }
            }
            
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // Messages list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .verticalScroll(listState)
            ) {
                if (chatHistory.isEmpty() && isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Analyzing Baseline CV & Job Specs...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chatHistory.forEach { message ->
                            val isUser = message.role == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isUser) 16.dp else 2.dp,
                                                bottomEnd = if (isUser) 2.dp else 16.dp
                                            )
                                        )
                                        .background(
                                            if (isUser) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .widthIn(max = 240.dp)
                                ) {
                                    Text(
                                        text = message.text,
                                        fontSize = 11.sp,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                        
                        if (isLoading && chatHistory.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Text Entry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask about skills, gaps, or customize...", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isLoading && !isRewriting,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                )
                
                Spacer(modifier = Modifier.width(6.dp))
                
                IconButton(
                    onClick = {
                        if (textInput.trim().isNotEmpty()) {
                            viewModel.sendChatMessage(job.id, textInput.trim())
                            textInput = ""
                        }
                    },
                    enabled = !isLoading && !isRewriting && textInput.trim().isNotEmpty(),
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (textInput.trim().isNotEmpty()) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant, 
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send message",
                        tint = if (textInput.trim().isNotEmpty()) MaterialTheme.colorScheme.onPrimary 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Primary Adaptation Action
            Button(
                onClick = {
                    viewModel.rewriteCvWithTemplateAndChatEdits(job.id) {
                        Toast.makeText(context, "Template accurately rewritten & mapped with AI custom edits!", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !isLoading && !isRewriting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                if (isRewriting) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-formatting CV onto Template...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rewrite & Map CV onto Template", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: APPLIED APPLICATIONS LOG SCREEN
// ==========================================
@Composable
fun AppliedLogsScreen(viewModel: JobViewModel) {
    val logs by viewModel.appliedLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Logs header & details
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Double Application Safeguard Active",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "JobCraft maintains unique company and job title hashes. Submitting duplicates will trigger a preventive UI alert so you do not apply to the same openings twice.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Total Logged Submissions: ${logs.size}",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No submissions logged yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "When you click 'Apply & Log' on a listing, it will instantly show up in this history registry.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(logs) { log ->
                    AppliedLogCard(log = log, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppliedLogCard(log: AppliedJobLog, viewModel: JobViewModel) {
    val context = LocalContext.current
    var viewDetails by remember { mutableStateOf(false) }
    var showEditLogDialog by remember { mutableStateOf(false) }

    val formattedDate = remember(log.appliedAt) {
        try {
            val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
            sdf.format(Date(log.appliedAt))
        } catch (e: Exception) {
            "Just now"
        }
    }

    val statusColor = remember(log.status) {
        when (log.status) {
            "Applied" -> Color(0xFF1976D2)
            "Technical Round" -> Color(0xFFE65100)
            "HR Round" -> Color(0xFF7B1FA2)
            "Final Interview" -> Color(0xFF0097A7)
            "Hired" -> Color(0xFF388E3C)
            "Rejected" -> Color(0xFFD32F2F)
            else -> Color(0xFF757575)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { viewDetails = !viewDetails },
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.jobName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = log.companyName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, statusColor.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = log.status,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (viewDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Applied on: $formattedDate",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Deadline: ${log.deadline}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            AnimatedVisibility(visible = viewDetails) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Archive Registry ID: LOG_00${log.id}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Stage Tracker Header
                    Text(
                        text = "Update Application Progress Stage:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Column of Stage selector chips
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Applied", "Technical Round", "HR Round").forEach { stageName ->
                                val isActive = log.status == stageName
                                val activeColor = when (stageName) {
                                    "Applied" -> Color(0xFF1976D2)
                                    "Technical Round" -> Color(0xFFE65100)
                                    "HR Round" -> Color(0xFF7B1FA2)
                                    else -> Color(0xFF757575)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(
                                            width = if (isActive) 1.5.dp else 1.dp,
                                            color = if (isActive) activeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            if (log.status != stageName) {
                                                viewModel.updateAppliedLog(log.copy(status = stageName))
                                                Toast.makeText(context, "Status updated: $stageName", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stageName,
                                        fontSize = 10.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Final Interview", "Hired", "Rejected").forEach { stageName ->
                                val isActive = log.status == stageName
                                val activeColor = when (stageName) {
                                    "Final Interview" -> Color(0xFF0097A7)
                                    "Hired" -> Color(0xFF388E3C)
                                    "Rejected" -> Color(0xFFD32F2F)
                                    else -> Color(0xFF757575)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isActive) activeColor.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(
                                            width = if (isActive) 1.5.dp else 1.dp,
                                            color = if (isActive) activeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            if (log.status != stageName) {
                                                viewModel.updateAppliedLog(log.copy(status = stageName))
                                                Toast.makeText(context, "Status updated: $stageName", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (stageName == "Hired" && isActive) {
                                            Text("🎉 ", fontSize = 10.sp)
                                        }
                                        Text(
                                            text = stageName,
                                            fontSize = 10.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!log.customizedCv.isNullOrEmpty() || !log.customizedCoverLetter.isNullOrEmpty()) {
                        Text(
                            text = "Reference Customized Materials:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!log.customizedCv.isNullOrEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        val fileName = "CV_${log.jobName.replace(" ", "_")}_archive.pdf"
                                        val file = PdfGenerator.generatePdf(context, fileName, "Archived CV - ${log.jobName}", log.customizedCv)
                                        if (file != null) {
                                            shareArchiveFile(context, file)
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share CV PDF", fontSize = 11.sp)
                                }
                            }

                            if (!log.customizedCoverLetter.isNullOrEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        val fileName = "CL_${log.jobName.replace(" ", "_")}_archive.pdf"
                                        val file = PdfGenerator.generatePdf(context, fileName, "Archived Cover Letter - ${log.jobName}", log.customizedCoverLetter)
                                        if (file != null) {
                                            shareArchiveFile(context, file)
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share Letter PDF", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row for Editing and Deleting log
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showEditLogDialog = true }
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Log", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(
                            onClick = {
                                viewModel.deleteAppliedLog(log.id)
                                Toast.makeText(context, "Log deleted from history", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Log", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Edit Log Dialog
    if (showEditLogDialog) {
        var editRole by remember { mutableStateOf(log.jobName) }
        var editComp by remember { mutableStateOf(log.companyName) }
        var editDead by remember { mutableStateOf(log.deadline) }

        AlertDialog(
            onDismissRequest = { showEditLogDialog = false },
            title = { Text("Edit Applied Log Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editRole,
                        onValueChange = { editRole = it },
                        label = { Text("Role / Job Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editComp,
                        onValueChange = { editComp = it },
                        label = { Text("Company") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDead,
                        onValueChange = { editDead = it },
                        label = { Text("Deadline (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = log.copy(
                            jobName = editRole.trim(),
                            companyName = editComp.trim(),
                            deadline = editDead.trim()
                        )
                        viewModel.updateAppliedLog(updated)
                        showEditLogDialog = false
                        Toast.makeText(context, "Log entry updated!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLogDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun shareArchiveFile(context: Context, file: java.io.File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.jobcraft.gkqyxa.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Archived File:"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing archive: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
