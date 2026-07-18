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
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the web content of a given URL and strips tags to leave readable text.
     */
    suspend fun fetchUrlContent(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "HTTP error: ${response.code} ${response.message}"
                }
                val html = response.body?.string() ?: ""
                val text = stripHtml(html)
                if (text.isEmpty()) {
                    return@withContext "Fetched page was empty or could not be parsed."
                }
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching URL: ${e.message}", e)
            "Error: ${e.localizedMessage ?: "Connection failed"}"
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
