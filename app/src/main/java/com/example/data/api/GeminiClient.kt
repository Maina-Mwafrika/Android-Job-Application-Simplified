package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"

    // MODIFIED: "gemini-3.5-flash" is not a confirmed valid model id and calls to a bad model id
    // return an error, which silently pushes the app into its "fabricate jobs" fallback path every
    // time. Using a documented stable model here. Google renames/deprecates models fairly often --
    // double check the current id at https://ai.google.dev/gemini-api/docs/models before shipping.
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // NEW: bundles the stripped text (for AI prompts) together with any schema.org JobPosting
    // JSON-LD blocks found in the raw HTML (for direct, trustworthy extraction -- no AI needed).
    data class FetchResult(val text: String, val jsonLdBlocks: List<String>)

    /**
     * NEW: Fetches a URL once and returns both the AI-prompt-ready stripped text and any raw
     * JSON-LD <script> blocks found on the page. Prefer this over fetchUrlContent() when you also
     * want structured-data extraction (see JobRepository.parseJsonLdJobPostings).
     */
    suspend fun fetchPage(url: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext FetchResult("HTTP error: ${response.code} ${response.message}", emptyList())
                }
                val html = response.body?.string() ?: ""
                val jsonLdBlocks = extractJsonLdBlocks(html)
                val text = stripHtml(html)
                if (text.isEmpty()) {
                    return@withContext FetchResult("Fetched page was empty or could not be parsed.", jsonLdBlocks)
                }
                FetchResult(text, jsonLdBlocks)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching URL: ${e.message}", e)
            FetchResult("Error: ${e.localizedMessage ?: "Connection failed"}", emptyList())
        }
    }

    /**
     * Fetches the web content of a given URL and strips tags to leave readable text.
     * MODIFIED: now implemented as a thin wrapper over fetchPage() so existing callers are unaffected.
     */
    suspend fun fetchUrlContent(url: String): String = fetchPage(url).text

    // NEW: pulls out every <script type="application/ld+json">...</script> block so callers can
    // look for schema.org JobPosting data -- the most reliable source of real apply links, since
    // it comes straight from the site's own markup (usually published for Google for Jobs).
    private fun extractJsonLdBlocks(html: String): List<String> {
        val blocks = mutableListOf<String>()
        try {
            val pattern = java.util.regex.Pattern.compile(
                "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>([\\s\\S]*?)</script>",
                java.util.regex.Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                matcher.group(1)?.trim()?.let { if (it.isNotEmpty()) blocks.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting JSON-LD blocks: ${e.message}")
        }
        return blocks
    }

    /**
     * NEW: Best-effort check that a URL actually resolves before we present it to the user as a
     * real "Apply" link. Some job boards reject HEAD requests from bots (403/429) even for a
     * genuinely live posting, so those codes are treated as "alive"; only a definite 404/410/5xx
     * (or a network failure) counts as unreachable.
     */
    suspend fun isUrlReachable(url: String): Boolean = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@withContext false

        fun codeLooksAlive(code: Int) = code in 200..399 || code == 401 || code == 403 || code == 429

        try {
            val headRequest = Request.Builder().url(url).head().header("User-Agent", USER_AGENT).build()
            okHttpClient.newCall(headRequest).execute().use { response ->
                if (codeLooksAlive(response.code)) return@withContext true
            }
        } catch (e: Exception) {
            // Some servers reject HEAD outright (405) -- fall through and retry with a ranged GET.
        }

        return@withContext try {
            val getRequest = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-256")
                .header("User-Agent", USER_AGENT)
                .build()
            okHttpClient.newCall(getRequest).execute().use { response ->
                codeLooksAlive(response.code)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun stripHtml(html: String): String {
        try {
            var text = html
            // Remove scripts and style sections
            text = text.replace(Regex("<script[^>]*?>[\\s\\S]*?<\\/script>", RegexOption.IGNORE_CASE), " ")
            text = text.replace(Regex("<style[^>]*?>[\\s\\S]*?<\\/style>", RegexOption.IGNORE_CASE), " ")
            // Remove html comments
            text = text.replace(Regex("<!--[\\s\\S]*?-->"), " ")

            // Preserve anchor links so they can be extracted dynamically by Gemini
            try {
                val anchorPattern = java.util.regex.Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", java.util.regex.Pattern.CASE_INSENSITIVE)
                val matcher = anchorPattern.matcher(text)
                val sb = java.lang.StringBuffer()
                while (matcher.find()) {
                    val url = matcher.group(1) ?: ""
                    val linkText = matcher.group(2) ?: ""
                    if (url.startsWith("http") || url.startsWith("/") || url.contains("job") || url.contains("career")) {
                        val cleanLinkText = linkText.replace(Regex("<[^>]*>"), "").trim()
                        if (cleanLinkText.isNotEmpty()) {
                            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("$cleanLinkText [Apply Link: $url]"))
                            continue
                        }
                    }
                    matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(linkText))
                }
                matcher.appendTail(sb)
                text = sb.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error preserving anchor links during stripping: ${e.message}")
            }

            // Replace remaining HTML tags with space
            text = text.replace(Regex("<[^>]*>"), " ")
            // Decode common HTML entities
            text = text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
            // Collapse whitespace
            text = text.replace(Regex("\\s+"), " ")
            // Limit character length to protect context window limits
            if (text.length > 40000) {
                text = text.substring(0, 40000) + "... [truncated]"
            }
            return text.trim()
        } catch (e: Exception) {
            return html
        }
    }

    /**
     * Generates content using the Gemini model.
     */
    suspend fun generateContent(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Key is missing. Please configure your API key in the Secrets Panel in AI Studio."
        }

        try {
            val jsonRequest = JSONObject()

            // Build contents array
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            jsonRequest.put("contents", contentsArray)

            // System instructions
            if (systemInstruction != null) {
                val sysInstObj = JSONObject()
                val sysPartsArray = JSONArray()
                val sysPartObj = JSONObject()
                sysPartObj.put("text", systemInstruction)
                sysPartsArray.put(sysPartObj)
                sysInstObj.put("parts", sysPartsArray)
                jsonRequest.put("systemInstruction", sysInstObj)
            }

            // Generation config
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.3)
            jsonRequest.put("generationConfig", genConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonRequest.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(responseStr).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP ${response.code} ${response.message}"
                    }
                    return@withContext "Error from Gemini API: $errMsg"
                }

                val candidates = JSONObject(responseStr).getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).getString("text")
                    }
                }
                "No response text received from Gemini."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}", e)
            "Error calling Gemini API: ${e.localizedMessage ?: "Request failed"}"
        }
    }
}