package com.example.data.repository

import android.util.Log
import com.example.data.database.AppliedLogDao
import com.example.data.database.CvDao
import com.example.data.database.JobDao
import com.example.data.api.GeminiClient
import com.example.data.api.JobBoardApiClient
import com.example.data.entity.AppliedJobLog
import com.example.data.entity.ScrapedJob
import com.example.data.entity.UserCv
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class JobRepository(
    private val cvDao: CvDao,
    private val jobDao: JobDao,
    private val appliedLogDao: AppliedLogDao
) {
    private val TAG = "JobRepository"

    val userCv: Flow<UserCv?> = cvDao.getUserCv()
    val allScrapedJobs: Flow<List<ScrapedJob>> = jobDao.getAllScrapedJobs()
    val allAppliedLogs: Flow<List<AppliedJobLog>> = appliedLogDao.getAllAppliedLogs()

    suspend fun getUserCvOnce(): UserCv? = cvDao.getUserCvOnce()

    suspend fun insertOrUpdateUserCv(cv: UserCv) {
        cvDao.insertOrUpdateUserCv(cv)
    }

    suspend fun getScrapedJobById(id: Int): ScrapedJob? {
        return jobDao.getScrapedJobById(id)
    }

    suspend fun updateScrapedJob(job: ScrapedJob) {
        jobDao.updateScrapedJob(job)
    }

    suspend fun deleteScrapedJobById(id: Int) {
        jobDao.deleteScrapedJobById(id)
    }

    suspend fun insertScrapedJob(job: ScrapedJob): Long {
        return jobDao.insertScrapedJob(job)
    }

    suspend fun clearAllScrapedJobs() {
        jobDao.clearAllScrapedJobs()
    }

    suspend fun insertAppliedLog(log: AppliedJobLog): Long {
        return appliedLogDao.insertAppliedLog(log)
    }

    suspend fun updateAppliedLog(log: AppliedJobLog) {
        appliedLogDao.updateAppliedLog(log)
    }

    suspend fun deleteAppliedLogById(id: Int) {
        appliedLogDao.deleteAppliedLogById(id)
    }

    suspend fun isAlreadyApplied(jobName: String, companyName: String): Boolean {
        return appliedLogDao.isAlreadyApplied(jobName, companyName)
    }

    /**
     * Downloads raw web page content, extracts jobs using local heuristics + Gemini AI, and returns them.
     *
     * MODIFIED pipeline order (most-trustworthy source first):
     *   0. Known ATS public API (Greenhouse / Lever) -- real, canonical apply links, no scraping at all.
     *   1. schema.org JobPosting JSON-LD embedded in the page -- also a real, site-published link.
     *   2. Local regex/heuristic scraping of the fetched HTML.
     *   3. Gemini AI extraction (or, if the page couldn't be fetched at all, Gemini *simulation* --
     *      now explicitly flagged with isSimulated = true instead of being presented as real).
     *   4. Every job that didn't come from step 0/1 gets its apply URL checked for reachability;
     *      anything that fails is also flagged isSimulated = true rather than silently shown as real.
     */
    suspend fun scrapeJobsFromUrl(url: String, targetTitle: String = ""): List<ScrapedJob> {
        // --- STEP 0: NEW -- known job-board public API shortcut ---
        // If this is a Greenhouse or Lever careers URL, skip scraping/AI entirely and hit their
        // public JSON API directly. This is the most reliable path in the whole app for "real links".
        JobBoardApiClient.detectBoard(url)?.let { board ->
            try {
                val apiJobs = when (board) {
                    is JobBoardApiClient.BoardMatch.Greenhouse -> JobBoardApiClient.fetchGreenhouseJobs(board.token)
                    is JobBoardApiClient.BoardMatch.Lever -> JobBoardApiClient.fetchLeverJobs(board.token)
                }
                if (apiJobs.isNotEmpty()) {
                    val filtered = filterByTargetTitle(apiJobs, targetTitle)
                    filtered.forEach { jobDao.insertScrapedJob(it) }
                    return filtered
                }
                Log.w(TAG, "Detected ${board::class.simpleName} board but API returned no jobs; falling back to generic scraping.")
            } catch (e: Exception) {
                Log.w(TAG, "Job board API path failed (${e.message}); falling back to generic scraping.")
            }
        }

        var pageText = ""
        var jsonLdBlocks: List<String> = emptyList()
        try {
            val fetchResult = GeminiClient.fetchPage(url)
            pageText = fetchResult.text
            jsonLdBlocks = fetchResult.jsonLdBlocks
        } catch (e: Exception) {
            pageText = "Error: ${e.message}"
        }

        // --- STEP 1: NEW -- schema.org JobPosting structured data ---
        // This comes straight from the page's own markup (most sites publish it for Google for
        // Jobs / SEO), so its URLs are real and it is never marked simulated.
        val jsonLdJobs = parseJsonLdJobPostings(jsonLdBlocks, url)

        val isBlockedOrFailed = pageText.startsWith("HTTP error:") || 
                                 pageText.startsWith("Error:") || 
                                 pageText.contains("Access Denied") || 
                                 pageText.contains("security check") ||
                                 pageText.trim().length < 200

        // --- STEP 2: local "intuitive code" heuristic scraping ---
        val localJobs = heuristicScraping(pageText, url, isBlockedOrFailed, targetTitle)

        // --- STEP 3: Gemini AI scraping (or simulation, if the page was unreachable) ---
        val aiJobs = mutableListOf<ScrapedJob>()
        val prompt = if (isBlockedOrFailed) {
            // Intelligent simulation fallback when job boards block raw bot HTTP crawlers
            """
                We are simulating a real-time web scrape of the job board at URL: "$url".
                Since the source webpage blocked the direct automated crawler with anti-bot policies, please use your intelligence to generate a list of 5 highly realistic, active, and specific job openings that would be found matching this query or board.
                
                The current local date is: July 18, 2026.
                
                ${if (targetTitle.isNotBlank()) "CRITICAL REQUIREMENT: You MUST generate job openings that are specifically relevant to the target job title, role, or keyword query: \"$targetTitle\". Every single generated job opening must fit this role." else ""}
                
                CRITICAL INSTRUCTION: Generate ONLY legitimate, professional job titles. Under no circumstances should you generate utility or page navigation titles (such as "Forgot Password", "Show Password", "Log In", "Sign Up", "Search", "Privacy Policy", etc.).
                
                For each job opening, generate:
                1. "title" (e.g., "${if (targetTitle.isNotBlank()) targetTitle else "Senior Android Developer"}", "Kotlin Software Engineer", "Product Designer", "IT Support Specialist", "Finance Analyst" depending on the URL and search terms)
                2. "company" (e.g., "Safaricom", "Equity Bank", "M-KOPA", "Google", "Microsoft", "Netflix" etc., or local firms if Nairobi-based Fuzu or BrighterMonday URLs are requested)
                3. "location" (e.g., "Remote", "Nairobi, Kenya", "San Francisco, CA" or "Hybrid" depending on the job board)
                4. "description" (a comprehensive description with specific responsibilities, qualifications, and core tech stack)
                5. "deadline" (calculate an exact upcoming application closing date between July 25, 2026 and August 30, 2026, formatted strictly as YYYY-MM-DD)
                6. "url" (generate a highly realistic, specific deep application URL for this particular job that matches the source site domain: if source URL is Indeed generate "https://www.indeed.com/viewjob?jk=...", if ZipRecruiter generate "https://www.ziprecruiter.com/jobs/...", if Fuzu generate "https://www.fuzu.com/kenya/jobs/...", if BrighterMonday generate "https://www.brightermonday.co.ke/jobs/...", if LinkedIn generate "https://www.linkedin.com/jobs/view/...", or if a custom careers site generate a direct job link under that domain host. MUST NOT default all URLs to LinkedIn.)
                7. "industry" (select the most accurate category from: "Technology & IT", "Finance & Banking", "Healthcare & Biotech", "Education & Academia", "Marketing & Sales", "Engineering & Construction", or "Other / General" based on the job role)
                
                Return ONLY a valid raw JSON array of these objects. Do not include any markdown backticks, explanations, or conversational filler.
            """.trimIndent()
        } else {
            // Real scraping
            """
                You are an advanced AI web scraper. Below is the text content extracted from a job search portal or listing page at URL: "$url".
                Please inspect this text carefully, extract any and all available job openings, and format them as a valid JSON array.
                
                The current local date is: July 18, 2026.
                
                ${if (targetTitle.isNotBlank()) "CRITICAL FILTER REQUIREMENT: If the user specified a target job title: \"$targetTitle\", please prioritize, extract, and return ONLY the job listings from the page text that are relevant or match this target job title or role query." else ""}
                
                CRITICAL WARNING ON JOB TITLES: The extracted page text may contain navigation menus, footer links, and user authentication/security elements (e.g., "Forgot Password", "Show Password", "Sign In", "Sign Up", "Log In", "Register", "Search", "Privacy Policy", "Cookies", etc.).
                You MUST ignore all such utility, user account, or navigation items. Do NOT extract them as jobs. Only extract real, professional, legitimate job openings with specific job titles (e.g. Software Engineer, Financial Accountant, Project Manager, Sales Executive, Nurse, etc.).
                
                CRITICAL WARNING ON DEEP LINKS: You MUST look for preserved `[Apply Link: <url>]` markers in the text that are directly associated with the job title to extract the actual direct deep URL. If the URL inside the marker is relative (e.g., starts with '/' or './'), you MUST resolve it against the source URL domain and host to build a full absolute URL. Ensure the returned "url" is the direct, specific application deep link for that job role, NOT a general homepage or listing search URL.
                
                Do not include any descriptive text, explanations, or conversational filler. Return ONLY a valid raw JSON array of objects.
                Each object in the array MUST contain these EXACT keys with string values:
                1. "title" (the position title, e.g., "Senior Android Engineer")
                2. "company" (the hiring company name, e.g., "Google")
                3. "location" (the location, e.g., "Remote" or "San Francisco, CA")
                4. "description" (a comprehensive summary of requirements, responsibilities, and about-the-role details)
                5. "deadline" (the exact application closing date normalized to YYYY-MM-DD. If not explicitly found in the text, calculate a realistic closing date between July 25, 2026 and August 30, 2026 based on the text or write an exact calculated date from relative terms like "Apply in 2 weeks" from current date July 18, 2026. Do NOT write general fallbacks like "2026-08-31" unless absolutely necessary; instead generate a dynamic exact deadline like "2026-08-05")
                6. "url" (the direct, absolute application link or deep URL for this specific role, e.g. "https://careers.google.com/jobs/results/123456". Prepend the host if relative. Must be a direct job details URL, not a general search/listing URL.)
                7. "industry" (select the most accurate category from: "Technology & IT", "Finance & Banking", "Healthcare & Biotech", "Education & Academia", "Marketing & Sales", "Engineering & Construction", or "Other / General" based on the job role)
                
                Here is the extracted webpage text content:
                ------------------
                $pageText
                ------------------
            """.trimIndent()
        }

        val systemInstruction = "You are a highly professional, precise data extraction and synthesis engine. You output perfectly formatted JSON matching the requested keys."

        try {
            val response = GeminiClient.generateContent(prompt, systemInstruction)
            if (!response.startsWith("Error")) {
                // MODIFIED: pass through whether this came from the "simulate from scratch" branch
                // so those jobs get isSimulated = true instead of looking identical to a real scrape.
                aiJobs.addAll(parseJobsFromJson(response, url, simulated = isBlockedOrFailed))
            } else {
                Log.w(TAG, "Gemini failed with error message, proceeding with local scraper results.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed with exception: ${e.message}, falling back gracefully to intuitive code scraper.")
        }

        // --- Merge and deduplicate results (structured-data jobs first) ---
        val finalJobs = mutableListOf<ScrapedJob>()
        finalJobs.addAll(jsonLdJobs)

        fun isDuplicateOf(candidate: ScrapedJob): Boolean =
            finalJobs.any {
                it.url == candidate.url ||
                    (it.title.equals(candidate.title, ignoreCase = true) && it.company.equals(candidate.company, ignoreCase = true))
            }

        if (aiJobs.isNotEmpty()) {
            aiJobs.forEach { if (!isDuplicateOf(it)) finalJobs.add(it) }
            localJobs.forEach { if (!isDuplicateOf(it)) finalJobs.add(it) }
        } else if (jsonLdJobs.isEmpty()) {
            // No AI credits or AI failed, and no structured data found: fall back to the intuitive
            // code results (these are already isSimulated = true when the page was unreachable,
            // see heuristicScraping below).
            finalJobs.addAll(localJobs)
        }

        val filteredByTitle = filterByTargetTitle(finalJobs, targetTitle)

        // --- STEP 4: NEW -- verify every non-structured-data URL before we present it as real ---
        // jsonLdJobs are already trustworthy (came from the page's own markup) but we still check
        // them too, since even published JobPosting data can go stale.
        val verifiedJobs = filteredByTitle.map { job ->
            if (job.isSimulated) {
                job
            } else {
                val reachable = try {
                    GeminiClient.isUrlReachable(job.url)
                } catch (e: Exception) {
                    false
                }
                if (reachable) job else job.copy(isSimulated = true)
            }
        }

        // Insert into database so user can view them immediately
        verifiedJobs.forEach { job ->
            jobDao.insertScrapedJob(job)
        }

        return verifiedJobs
    }

    private fun filterByTargetTitle(jobs: List<ScrapedJob>, targetTitle: String): List<ScrapedJob> {
        if (targetTitle.isBlank()) return jobs
        val queryWords = targetTitle.lowercase().split("\\s+".toRegex()).filter { it.length >= 3 }
        if (queryWords.isEmpty()) return jobs
        return jobs.filter { job ->
            val combinedText = (job.title + " " + job.description).lowercase()
            queryWords.any { word -> combinedText.contains(word) }
        }
    }

    /**
     * NEW: Parses schema.org JobPosting objects out of <script type="application/ld+json"> blocks.
     * This is the most trustworthy source of job data in the whole pipeline because it comes
     * directly from the site's own markup (typically published for Google for Jobs / SEO), and
     * that includes the site's own canonical apply URL -- nothing here is guessed or generated.
     */
    private fun parseJsonLdJobPostings(blocks: List<String>, sourceUrl: String): List<ScrapedJob> {
        val results = mutableListOf<ScrapedJob>()

        fun extractFromObject(obj: JSONObject) {
            val type = obj.opt("@type")
            val isJobPosting = when (type) {
                is String -> type.equals("JobPosting", ignoreCase = true)
                is JSONArray -> (0 until type.length()).any { (type.optString(it, "")).equals("JobPosting", ignoreCase = true) }
                else -> false
            }
            if (!isJobPosting) return

            val title = obj.optString("title", "").trim()
            if (title.isEmpty()) return

            val company = when (val org = obj.opt("hiringOrganization")) {
                is JSONObject -> org.optString("name", "").trim()
                is String -> org.trim()
                else -> ""
            }.ifEmpty { "Confidential" }

            val location = try {
                val jobLocation = obj.opt("jobLocation")
                val locObj = when (jobLocation) {
                    is JSONArray -> if (jobLocation.length() > 0) jobLocation.optJSONObject(0) else null
                    is JSONObject -> jobLocation
                    else -> null
                }
                val address = locObj?.optJSONObject("address")
                listOfNotNull(
                    address?.optString("addressLocality")?.takeIf { it.isNotBlank() },
                    address?.optString("addressRegion")?.takeIf { it.isNotBlank() },
                    address?.optString("addressCountry")?.takeIf { it.isNotBlank() }
                ).joinToString(", ").ifEmpty {
                    val locationType = obj.optString("jobLocationType", "")
                    if (locationType.contains("TELECOMMUTE", ignoreCase = true)) "Remote" else "Not Specified"
                }
            } catch (e: Exception) {
                "Not Specified"
            }

            val description = obj.optString("description", "No details provided.")
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifEmpty { "No details provided." }

            val deadline = obj.optString("validThrough", "").let {
                if (it.length >= 10) it.substring(0, 10) else "2026-08-31"
            }

            val applyUrl = obj.optString("url", "").ifBlank { sourceUrl }
            val resolvedUrl = if (applyUrl.startsWith("http://") || applyUrl.startsWith("https://")) {
                applyUrl
            } else {
                resolveRelativeUrl(sourceUrl, applyUrl)
            }

            val industry = heuristicDetermineIndustry(title, description)

            results.add(
                ScrapedJob(
                    title = title,
                    company = company,
                    location = location,
                    description = description,
                    deadline = deadline,
                    url = resolvedUrl,
                    industry = industry,
                    isSimulated = false
                )
            )
        }

        blocks.forEach { raw ->
            try {
                val trimmed = raw.trim()
                when {
                    trimmed.startsWith("[") -> {
                        val arr = JSONArray(trimmed)
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.let { extractFromObject(it) }
                        }
                    }
                    trimmed.startsWith("{") -> {
                        val obj = JSONObject(trimmed)
                        val graph = obj.optJSONArray("@graph") // some sites wrap postings in @graph
                        if (graph != null) {
                            for (i in 0 until graph.length()) {
                                graph.optJSONObject(i)?.let { extractFromObject(it) }
                            }
                        } else {
                            extractFromObject(obj)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Skipping unparsable JSON-LD block: ${e.message}")
            }
        }

        return results
    }

    /**
     * Local "intuitive code" scraping using Regex and heuristic pattern matching.
     */
    private fun heuristicScraping(pageText: String, sourceUrl: String, isBlocked: Boolean, targetTitle: String = ""): List<ScrapedJob> {
        val jobs = mutableListOf<ScrapedJob>()
        val seenUrls = mutableSetOf<String>()

        if (isBlocked || pageText.length < 200) {
            // Intelligent local simulation of job boards for high-fidelity fallback when page is blocked
            val searchKeyword = extractKeywordFromUrl(sourceUrl)
            val baseDomainCompany = extractCompanyFromUrl(sourceUrl) ?: "Apex Global"
            
            val simulatedRoles = if (targetTitle.isNotBlank()) {
                listOf(
                    targetTitle to (extractCompanyFromUrl(sourceUrl) ?: "Safaricom PLC"),
                    "Senior $targetTitle" to "M-KOPA Kenya",
                    "Lead $targetTitle" to "Equity Bank Group",
                    "Junior $targetTitle" to "Cellulant",
                    "Technical $targetTitle Specialist" to "Andela"
                )
            } else {
                when (searchKeyword.lowercase()) {
                    "android", "kotlin", "compose" -> listOf(
                        "Senior Android Engineer" to "Safaricom PLC",
                        "Kotlin Developer" to "M-KOPA Kenya",
                        "Mobile App Developer (Compose)" to "Equity Bank Group",
                        "Junior Android Developer" to "Cellulant",
                        "Android Tech Lead" to "SokoWatch (Wasoko)"
                    )
                    "software", "developer", "engineer" -> listOf(
                        "Full Stack Software Engineer" to "Microsoft Africa Development Center",
                        "Backend Systems Engineer" to "Kopo Kopo",
                        "Frontend Web Specialist" to "Andela",
                        "DevOps Engineer" to "Copias Kenya",
                        "Junior Software Engineer" to "MyDawa"
                    )
                    "finance", "account", "banking" -> listOf(
                        "Treasury Management Analyst" to "I&M Bank",
                        "Senior Financial Accountant" to "NCBA Group",
                        "Internal Auditor" to "Kenya Commercial Bank",
                        "Risk & Compliance Officer" to "Equity Bank Group",
                        "Investment Portfolio Associate" to "Britam"
                    )
                    "health", "medical" -> listOf(
                        "Clinical Research Coordinator" to "KEMRI",
                        "Digital Health Product Owner" to "Infectious Diseases Institute",
                        "Telemedicine Specialist" to "MyDawa",
                        "Health Informatics Analyst" to "Amref Health Africa",
                        "Laboratory Services Manager" to "Pathcare Kenya"
                    )
                    "marketing", "sales" -> listOf(
                        "Digital Marketing Manager" to "Jumia Group",
                        "Sales Growth Specialist" to "M-KOPA Kenya",
                        "Brand Communications Specialist" to "Safaricom",
                        "SEO & Content Strategist" to "Ringier One Africa Media",
                        "Corporate Account Representative" to "Copias Kenya"
                    )
                    else -> listOf(
                        "Technical Support Specialist" to "Safaricom PLC",
                        "Data Analyst" to "SokoWatch",
                        "Operations Coordinator" to "Sendy Kenya",
                        "Product Manager" to "Airtel Kenya",
                        "IT Systems Administrator" to "Co-operative Bank"
                    )
                }
            }

            simulatedRoles.forEachIndexed { index, (title, company) ->
                val desc = buildHeuristicDescription(title, company, "Nairobi, Kenya")
                val industry = heuristicDetermineIndustry(title, desc)
                val deadlineDate = "2026-08-${15 + index}"
                val lowerSource = sourceUrl.lowercase()
                val slugTitle = title.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-')
                val deepUrl = when {
                    lowerSource.contains("indeed") -> {
                        "https://www.indeed.com/viewjob?jk=${slugTitle}${1000 + index}"
                    }
                    lowerSource.contains("ziprecruiter") -> {
                        "https://www.ziprecruiter.com/jobs/$slugTitle-${1000 + index}"
                    }
                    lowerSource.contains("fuzu") -> {
                        "https://www.fuzu.com/kenya/jobs/$slugTitle-${1000 + index}"
                    }
                    lowerSource.contains("brightermonday") -> {
                        "https://www.brightermonday.co.ke/jobs/$slugTitle-${1000 + index}"
                    }
                    lowerSource.contains("linkedin") -> {
                        "https://www.linkedin.com/jobs/view/${928400000 + index + (Math.random() * 10000).toInt()}"
                    }
                    lowerSource.startsWith("http") -> {
                        try {
                            val uri = android.net.Uri.parse(sourceUrl)
                            val host = uri.host ?: "careers.site"
                            "https://$host/jobs/$slugTitle-${1000 + index}"
                        } catch (e: Exception) {
                            "https://www.linkedin.com/jobs/view/${928400000 + index}"
                        }
                    }
                    else -> "https://www.linkedin.com/jobs/view/${928400000 + index}"
                }

                jobs.add(
                    ScrapedJob(
                        title = title,
                        company = company,
                        location = "Hybrid (Nairobi, Kenya)",
                        description = desc,
                        deadline = deadlineDate,
                        url = deepUrl,
                        industry = industry,
                        // MODIFIED: this whole branch is a made-up fallback (title, company, and
                        // especially the URL are all fabricated locally) -- it must never be shown
                        // to the user as if it were a verified real posting.
                        isSimulated = true
                    )
                )
            }
            return jobs
        }

        // Real page scraping from pageText using Regex
        // Pattern 1: Look for preserved anchor links of format: Label [Apply Link: url]
        try {
            val pattern = java.util.regex.Pattern.compile("([^\\n.\\[]+)\\[Apply Link: ([^\\]]+)\\]", java.util.regex.Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(pageText)
            
            while (matcher.find()) {
                val rawTitle = matcher.group(1)?.trim() ?: ""
                val jobUrl = matcher.group(2)?.trim() ?: ""
                
                if (jobUrl.isEmpty() || seenUrls.contains(jobUrl)) continue
                if (!isValidJobTitle(rawTitle)) continue
                
                // Clean title and try to extract company name
                var title = rawTitle
                var company = ""
                
                val atIndex = title.indexOf(" at ", ignoreCase = true)
                val hyphenIndex = title.indexOf(" - ")
                val colonIndex = title.indexOf(":")
                
                if (atIndex != -1) {
                    company = title.substring(atIndex + 4).trim()
                    title = title.substring(0, atIndex).trim()
                } else if (hyphenIndex != -1) {
                    val part1 = title.substring(0, hyphenIndex).trim()
                    val part2 = title.substring(hyphenIndex + 3).trim()
                    if (isJobTitleLike(part2)) {
                        company = part1
                        title = part2
                    } else {
                        company = part2
                        title = part1
                    }
                } else if (colonIndex != -1) {
                    val part1 = title.substring(0, colonIndex).trim()
                    val part2 = title.substring(colonIndex + 1).trim()
                    if (isJobTitleLike(part2)) {
                        company = part1
                        title = part2
                    } else {
                        company = part2
                        title = part1
                    }
                }
                
                if (company.isEmpty()) {
                    company = extractCompanyFromUrl(jobUrl) ?: extractCompanyFromUrl(sourceUrl) ?: "Hiring Team"
                }
                
                // Determine location
                var location = "Remote / Hybrid"
                if (pageText.contains("Nairobi", ignoreCase = true) || jobUrl.contains(".ke") || sourceUrl.contains(".ke") || company.lowercase().contains("safaricom") || company.lowercase().contains("equity")) {
                    location = "Nairobi, Kenya"
                } else if (pageText.contains("San Francisco", ignoreCase = true)) {
                    location = "San Francisco, CA"
                } else if (pageText.contains("London", ignoreCase = true)) {
                    location = "London, UK"
                } else if (pageText.contains("Remote", ignoreCase = true)) {
                    location = "Remote"
                }
                
                val description = buildHeuristicDescription(title, company, location)
                val industry = heuristicDetermineIndustry(title, description)
                val deadline = "2026-08-18"
                
                val resolvedUrl = if (jobUrl.startsWith("http://") || jobUrl.startsWith("https://")) {
                    jobUrl
                } else {
                    resolveRelativeUrl(sourceUrl, jobUrl)
                }
                
                jobs.add(
                    ScrapedJob(
                        title = title,
                        company = company,
                        location = location,
                        description = description,
                        deadline = deadline,
                        url = resolvedUrl,
                        industry = industry
                        // isSimulated left at default (false): this URL was actually found in the
                        // page's own anchor tags, not guessed -- but it still goes through the
                        // reachability check in scrapeJobsFromUrl() before being trusted fully.
                    )
                )
                seenUrls.add(jobUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heuristic link parsing error: ${e.message}")
        }

        // Pattern 2: Scan individual text lines for job-like phrases
        if (jobs.isEmpty()) {
            try {
                val lines = pageText.split("\n")
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.length in 10..80 && isJobTitleLike(line)) {
                        val title = line
                        var company = "Hiring Partner"
                        
                        // Look at the adjacent line for company names
                        if (i + 1 < lines.size) {
                            val nextLine = lines[i + 1].trim()
                            if (nextLine.isNotEmpty() && nextLine.length < 40 && !isJobTitleLike(nextLine)) {
                                company = nextLine
                            }
                        }
                        
                        var location = "Remote"
                        if (pageText.contains("Nairobi", ignoreCase = true)) {
                            location = "Nairobi, Kenya"
                        }
                        
                        val description = buildHeuristicDescription(title, company, location)
                        val industry = heuristicDetermineIndustry(title, description)
                        
                        jobs.add(
                            ScrapedJob(
                                title = title,
                                company = company,
                                location = location,
                                description = description,
                                deadline = "2026-08-18",
                                url = sourceUrl,
                                industry = industry,
                                // MODIFIED: we don't have a real per-job link here at all -- it just
                                // falls back to the source listing page URL -- so mark it simulated
                                // rather than implying it's a direct apply link.
                                isSimulated = true
                            )
                        )
                        if (jobs.size >= 12) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Heuristic line scanning error: ${e.message}")
            }
        }

        // Ultimate fallback
        if (jobs.isEmpty()) {
            val fallbackTitles = listOf("Senior Android Engineer", "Backend Developer (Kotlin)", "Product Manager")
            val fallbackCompanies = listOf("Apex Solutions", "Equity Group Labs", "Tech Innovators")
            fallbackTitles.forEachIndexed { idx, t ->
                val comp = fallbackCompanies[idx]
                val desc = buildHeuristicDescription(t, comp, "Remote / Hybrid")
                val ind = heuristicDetermineIndustry(t, desc)
                jobs.add(
                    ScrapedJob(
                        title = t,
                        company = comp,
                        location = "Remote",
                        description = desc,
                        deadline = "2026-08-18",
                        url = sourceUrl,
                        industry = ind,
                        // MODIFIED: entirely made-up placeholder content.
                        isSimulated = true
                    )
                )
            }
        }

        return jobs
    }

    private fun isJobTitleLike(text: String): Boolean {
        val lower = text.lowercase()
        val jobKeywords = listOf(
            "developer", "engineer", "designer", "specialist", "analyst", "manager", "lead", "officer", 
            "accountant", "nurse", "doctor", "teacher", "professor", "intern", "associate", "expert", 
            "consultant", "representative", "operator", "administrator", "coordinator", "recruiter",
            "scrum master", "architect", "programmer", "writer", "editor", "auditor", "cashier", "clerk",
            "technician", "chef", "cook", "driver", "agent", "sales", "marketer", "executive", "advisor", 
            "planner", "supervisor", "director", "vp", "head", "controller", "treasurer", "teller", 
            "mechanic", "pharmacist", "therapist", "helper", "assistant", "receptionist", "instructor", 
            "tutor", "lecturer", "educator", "counselor", "worker", "staff", "fellow", "strategist", 
            "practitioner", "biologist", "scientist", "researcher", "partner", "hr", "marketing", "admin"
        )
        return jobKeywords.any { lower.contains(it) }
    }

    private fun isValidJobTitle(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 5 || trimmed.length > 90) return false
        
        val lower = trimmed.lowercase()
        val forbiddenSubstrings = listOf(
            "password", "username", "sign-up", "sign up", "sign-in", "sign in", "log-in", "log in", 
            "logout", "log out", "register", "join now", "create account", "forgot", "cookie", 
            "privacy", "terms of", "terms &", "help center", "support", "about us", "contact us", 
            "careers", "all openings", "home", "search", "subscribe", "newsletter", "copyright", 
            "settings", "profile", "dashboard", "feedback", "frequently asked", "faq", "view cart", 
            "checkout", "navigation", "menu", "close", "cancel", "dismiss", "next", "previous", 
            "page ", "read more", "learn more", "view details", "click here", "apply now", "apply today", 
            "apply here", "go to", "javascript", "browser", "css", "html", "loading", "error", 
            "server", "status", "api key", "config", "notification", "alert", "accept", "decline",
            "agree", "powered by", "all rights", "developed by", "designed by"
        )
        if (forbiddenSubstrings.any { lower.contains(it) }) {
            return false
        }
        
        // Exclude lines with only numbers, symbols, or single words that are not typical job components
        if (trimmed.matches(Regex("[^a-zA-Z]+"))) return false
        
        return isJobTitleLike(trimmed)
    }

    private fun extractKeywordFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("android") -> "android"
            lower.contains("kotlin") -> "kotlin"
            lower.contains("compose") -> "compose"
            lower.contains("software") -> "software"
            lower.contains("developer") -> "developer"
            lower.contains("engineer") -> "engineer"
            lower.contains("finance") || lower.contains("account") || lower.contains("banking") -> "finance"
            lower.contains("health") || lower.contains("medical") -> "health"
            lower.contains("marketing") || lower.contains("sales") -> "marketing"
            else -> "software"
        }
    }

    private fun extractCompanyFromUrl(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return null
            val cleanHost = host.replace("www.", "").replace("jobs.", "").replace("careers.", "")
            val dotIndex = cleanHost.indexOf('.')
            if (dotIndex != -1) {
                cleanHost.substring(0, dotIndex).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else {
                cleanHost.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveRelativeUrl(sourceUrl: String, relativePath: String): String {
        return try {
            val base = java.net.URL(sourceUrl)
            java.net.URL(base, relativePath).toString()
        } catch (e: Exception) {
            try {
                val uri = java.net.URI(sourceUrl)
                val scheme = uri.scheme ?: "https"
                val host = uri.host ?: ""
                val port = if (uri.port != -1) ":${uri.port}" else ""
                val cleanPath = when {
                    relativePath.startsWith("/") -> relativePath
                    relativePath.startsWith("./") -> relativePath.substring(1)
                    else -> "/$relativePath"
                }
                "$scheme://$host$port$cleanPath"
            } catch (ex: Exception) {
                if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) relativePath else sourceUrl
            }
        }
    }

    private fun buildHeuristicDescription(title: String, company: String, location: String): String {
        return """
            We are looking for a qualified $title to join our dynamic team at $company in $location.
            
            Key Responsibilities:
            - Design, develop, test, and deploy software or domain-specific deliverables.
            - Collaborate with cross-functional teams to outline requirements and architecture.
            - Troubleshoot, optimize, and maintain systems or client programs to ensure maximum performance.
            - Adhere to the team's best practices, documentation patterns, and agile workflows.
            
            Required Qualifications:
            - Professional experience working as a $title or similar role.
            - Strong analytical thinking, problem-solving skills, and attention to detail.
            - Outstanding communication skills and willingness to collaborate closely within a team.
        """.trimIndent()
    }

    private fun heuristicDetermineIndustry(title: String, description: String): String {
        val text = "$title $description".lowercase()
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


    /**
     * Tailor the user's CV and draft a cover letter for a specific job description.
     */
    suspend fun customizeCvAndCoverLetter(jobId: Int): Pair<String, String> {
        val job = jobDao.getScrapedJobById(jobId) ?: throw Exception("Job listing not found in database.")
        val cv = cvDao.getUserCvOnce() ?: throw Exception("Please configure and save your CV details first.")

        // 1. Customize CV Prompt
        val cvPrompt = """
            You are an expert career counselor and resume tailorer. 
            
            I want you to customize my current resume to make it highly aligned with this specific job description:
            - **Job Title**: ${job.title}
            - **Company**: ${job.company}
            - **Location**: ${job.location}
            - **Description**: ${job.description}
            
            Here is my current resume text and candidate details:
            - **Full Name**: ${cv.fullName}
            - **Email**: ${cv.email}
            - **Phone**: ${cv.phone}
            - **Current Resume**: 
            ${cv.rawCvText}
            
            ${if (!cv.cvTemplate.isNullOrBlank()) "Use this exact CV Layout Template provided by the user:\n${cv.cvTemplate}" else "Use the exact Executive CV Layout Template structure below."}
            
            CRITICAL FORMATTING INSTRUCTIONS:
            1. Preserve the exact layout structure, headings, and formatting sections:
               - Header: # [FULL NAME IN ALL CAPS]
                         [Professional Subtitle / Tagline Roles separated by •]
                         [Phone] • [Email] • [LinkedIn URL] • [Location]
               - ## PROFESSIONAL SUMMARY
               - ## TECHNICAL SKILLS (Categorized bullet list: Languages & Frameworks, Data & Analytics, Databases, Full-Stack & Cloud, IT & Systems, Tools & Platforms, Research Methods)
               - ## WORK EXPERIENCE (Role Title | *Date Range*, Company Name | *Location*, bulleted achievements)
               - ## VOLUNTEER EXPERIENCE (if present)
               - ## EDUCATION (Degree | *Date Range*, Institution Name, Honors & Key modules)
               - ## LEADERSHIP & ACHIEVEMENTS
               - ## INTERESTS & PROFESSIONAL INTERESTS
            
            2. Optimize, rewrite, and tailor the candidate's achievements, skills, summary, and work bullet points to emphasize relevant skills, technologies, keywords, and quantified impact matching the target job description.
            3. Do not omit the candidate's real experiences, but align them cleanly with the job requirements.
            4. Output clean, structured Markdown text.
        """.trimIndent()

        // 2. Draft Cover Letter Prompt
        val clPrompt = """
            You are an expert executive recruiter.
            
            Write a highly customized, compelling, and professional Cover Letter tailored to this specific job description:
            - **Job Title**: ${job.title}
            - **Company**: ${job.company}
            - **Location**: ${job.location}
            - **Description**: ${job.description}
            
            Here are my details:
            - **Full Name**: ${cv.fullName}
            - **Email**: ${cv.email}
            - **Phone**: ${cv.phone}
            - **Background Highlights**:
            ${cv.rawCvText}
            
            The cover letter should contain:
            1. Sender info (My name, email, phone)
            2. Date (Current date is July 18, 2026)
            3. Hiring Manager salutation
            4. Compelling introduction matching the company and role
            5. Bulleted value statements directly referencing requirements in the job description and linking them to my background highlights
            6. Professional call to action/conclusion
            
            Return the Cover Letter in clean text format. Do not use Markdown headings like # or ## if possible, but write elegant standard letter paragraphs.
        """.trimIndent()

        val customizedCv = GeminiClient.generateContent(cvPrompt)
        val coverLetter = GeminiClient.generateContent(clPrompt)

        // Save back to db
        val updatedJob = job.copy(
            customizedCv = customizedCv,
            customizedCoverLetter = coverLetter
        )
        jobDao.updateScrapedJob(updatedJob)

        return Pair(customizedCv, coverLetter)
    }

    private fun parseJobsFromJson(jsonString: String, sourceUrl: String, simulated: Boolean = false): List<ScrapedJob> {
        val list = mutableListOf<ScrapedJob>()
        try {
            var cleanJson = jsonString.trim()
            
            // Handle markdown wrapping blocks
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substringAfter("```")
                if (cleanJson.startsWith("json", ignoreCase = true)) {
                    cleanJson = cleanJson.substringAfter("json")
                }
                if (cleanJson.endsWith("```")) {
                    cleanJson = cleanJson.substringBeforeLast("```")
                }
                cleanJson = cleanJson.trim()
            }

            // Find JSON Array start/end bounds
            val startIndex = cleanJson.indexOf('[')
            val endIndex = cleanJson.lastIndexOf(']')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                cleanJson = cleanJson.substring(startIndex, endIndex + 1)
            }

            if (!cleanJson.startsWith("[")) {
                // Try parsing as single object if it's not an array
                val objStart = cleanJson.indexOf('{')
                val objEnd = cleanJson.lastIndexOf('}')
                if (objStart != -1 && objEnd != -1 && objEnd > objStart) {
                    val singleObjStr = cleanJson.substring(objStart, objEnd + 1)
                    val obj = JSONObject(singleObjStr)
                    val job = parseJobObject(obj, sourceUrl, simulated)
                    if (isValidJobTitle(job.title)) {
                        list.add(job)
                    }
                    return list
                }
                throw Exception("JSON is not an array or object: $cleanJson")
            }

            val array = JSONArray(cleanJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val job = parseJobObject(obj, sourceUrl, simulated)
                if (isValidJobTitle(job.title)) {
                    list.add(job)
                } else {
                    Log.d(TAG, "Filtering out non-job title extracted by Gemini: ${job.title}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing JSON jobs array: ${e.message}", e)
            Log.d(TAG, "Raw response was: $jsonString")
        }
        return list
    }

    private fun parseJobObject(obj: JSONObject, defaultUrl: String, simulated: Boolean = false): ScrapedJob {
        val title = obj.optString("title", "Job Opportunity").trim()
        val company = obj.optString("company", "Confidential").trim()
        val location = obj.optString("location", "Not Specified").trim()
        val description = obj.optString("description", "No details provided.").trim()
        
        var deadline = obj.optString("deadline", "2026-08-31").trim()
        if (deadline.isEmpty() || deadline.equals("null", ignoreCase = true)) {
            deadline = "2026-08-31"
        }
        
        val rawUrl = obj.optString("url", defaultUrl).trim()
        val url = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            resolveRelativeUrl(defaultUrl, rawUrl)
        }
        val industry = obj.optString("industry", "Other / General").trim()
        return ScrapedJob(
            title = title,
            company = company,
            location = location,
            description = description,
            deadline = deadline,
            url = url,
            industry = industry,
            // MODIFIED: threaded through from the caller so jobs born from the "simulate from
            // scratch" prompt branch are honestly labeled instead of looking like a real scrape.
            isSimulated = simulated
        )
    }

    /**
     * Compare this job with the user's master CV and calculate match score / feedback.
     */
    suspend fun calculateMatchForJob(jobId: Int): Pair<Int, String>? {
        val job = jobDao.getScrapedJobById(jobId) ?: return null
        val cv = cvDao.getUserCvOnce() ?: return null

        val prompt = """
            You are an advanced career matching AI. Compare the user's CV with the job details and evaluate their alignment.
            
            USER CV:
            ${cv.rawCvText}
            
            JOB TITLE: ${job.title}
            COMPANY: ${job.company}
            DESCRIPTION: ${job.description}
            
            Evaluate the alignment and return ONLY a valid JSON object with these exact two keys:
            - "score" (integer from 0 to 100 representing percentage match)
            - "feedback" (a short 1-sentence explanation of major alignment or critical gap, max 12 words)
            
            Return ONLY the raw JSON object. Do not include ```json blocks, markdown formatting, or any wrapper.
        """.trimIndent()

        val systemInstruction = "You are a precise, data-oriented career parser. You respond only with a raw JSON object containing score and feedback."

        return try {
            val response = GeminiClient.generateContent(prompt, systemInstruction)
            val cleanResponse = response.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(cleanResponse)
            val score = json.getInt("score")
            val feedback = json.getString("feedback")

            val updated = job.copy(matchScore = score, matchFeedback = feedback)
            jobDao.updateScrapedJob(updated)

            Pair(score, feedback)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating match with Gemini for job $jobId: ${e.message}", e)
            val score = calculateFallbackScore(cv.rawCvText, job.title, job.description)
            val feedback = "Analyzed core skills and technical keywords match."
            val updated = job.copy(matchScore = score, matchFeedback = feedback)
            jobDao.updateScrapedJob(updated)
            Pair(score, feedback)
        }
    }

    private fun calculateFallbackScore(cvText: String, title: String, description: String): Int {
        var score = 55
        val cvLower = cvText.lowercase()
        val descLower = description.lowercase()
        val titleLower = title.lowercase()

        val titleKeywords = listOf("android", "kotlin", "compose", "java", "developer", "engineer", "software", "senior", "lead", "frontend", "backend")
        titleKeywords.forEach { word ->
            if (titleLower.contains(word) && cvLower.contains(word)) {
                score += 8
            }
        }

        val skills = listOf("kotlin", "compose", "git", "retrofit", "api", "mvvm", "room", "coroutine", "flow", "jetpack", "architecture", "ui", "ux")
        skills.forEach { skill ->
            if (descLower.contains(skill) && cvLower.contains(skill)) {
                score += 5
            }
        }

        return score.coerceIn(0, 100)
    }
}