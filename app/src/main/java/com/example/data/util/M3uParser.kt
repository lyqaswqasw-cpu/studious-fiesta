package com.example.data.util

import android.util.Log
import com.example.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object M3uParser {

    private const val TAG = "M3uParser"

    suspend fun parse(url: String, categoryName: String): List<Channel> = withContext(Dispatchers.IO) {
        val rawItems = mutableListOf<ChannelClusteringEngine.RawStream>()
        try {
            val connection = URL(url.trim()).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "IPTVSmarters/1.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                val lines = content.lines()

                var currentName = ""
                var currentLogo = ""
                var currentGroup = categoryName.trim().ifBlank { "عام" }

                val logoRegex = Regex("""tvg-logo="([^"]+)"""")
                val groupRegex = Regex("""group-title="([^"]+)"""")

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXTINF:")) {
                        // Extract logo
                        currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1) ?: ""

                        // Extract group-title if present and category is generic
                        val parsedGroup = groupRegex.find(trimmed)?.groupValues?.get(1)
                        if (!parsedGroup.isNullOrBlank() && categoryName.isBlank()) {
                            currentGroup = parsedGroup.trim()
                        }

                        // Extract name (after last comma)
                        val namePart = trimmed.substringAfterLast(",", "").trim()
                        if (namePart.isNotEmpty()) {
                            currentName = namePart
                        }
                    } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        // It's a stream URL
                        if (currentName.isNotEmpty()) {
                            val streamType = when {
                                trimmed.endsWith(".m3u8", ignoreCase = true) -> "m3u8"
                                trimmed.endsWith(".ts", ignoreCase = true) -> "ts"
                                else -> "normal"
                            }
                            rawItems.add(
                                ChannelClusteringEngine.RawStream(
                                    rawName = currentName,
                                    streamUrl = trimmed,
                                    logoUrl = currentLogo,
                                    category = currentGroup,
                                    streamType = streamType
                                )
                            )
                            currentName = ""
                            currentLogo = ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing M3U from $url: ${e.message}", e)
        }

        // Apply intelligent quality clustering & deduplication
        ChannelClusteringEngine.clusterRawStreams(rawItems, categoryName.ifBlank { "عام" })
    }
}
