package com.wavestream.core

import kotlin.math.abs

/**
 * Levenshtein distance — used for fuzzy string matching in search results.
 * Mirrors CloudStream's `Levenshtein.kt`.
 */
fun levenshtein(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    if (m == 0) return n
    if (n == 0) return m

    val prev = IntArray(n + 1) { it }
    val curr = IntArray(n + 1)

    for (i in 1..m) {
        curr[0] = i
        for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        prev.indices.forEach { prev[it] = curr[it] }
    }
    return prev[n]
}

/**
 * Fuzzy match score — returns 0.0 (no match) to 1.0 (perfect match).
 */
fun fuzzyMatch(query: String, target: String): Double {
    if (query.isBlank()) return 1.0
    if (target.isBlank()) return 0.0

    val q = query.lowercase().trim()
    val t = target.lowercase().trim()

    if (t.contains(q)) return 1.0
    if (q.contains(t)) return 0.9

    val distance = levenshtein(q, t)
    val maxLen = maxOf(q.length, t.length)
    return 1.0 - (distance.toDouble() / maxLen)
}

/**
 * Sanitize a string for use as a filename.
 * Mirrors CloudStream's `sanitizeFilename`.
 */
fun sanitizeFilename(name: String, allowSpaces: Boolean = false): String {
    val sanitized = name.replace(Regex("[^A-Za-z0-9._\\- ]"), if (allowSpaces) " " else "_")
    return sanitized.trim().ifBlank { "unknown" }
}

/**
 * Format bytes as a human-readable string (e.g. "1.5 GB").
 */
fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "%.1f GB".format(gb)
        mb >= 1 -> "%.1f MB".format(mb)
        kb >= 1 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}

/**
 * Format milliseconds as a time string (e.g. "1:23:45" or "23:45").
 */
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Format seconds as a time string.
 */
fun formatTime(seconds: Double): String = formatTime((seconds * 1000).toLong())

/**
 * Parse a duration string (e.g. "1h 30min" or "1:30:00") into minutes.
 */
fun parseDuration(input: String?): Int? {
    val clean = input?.trim()?.replace(" ", "") ?: return null

    // Try "1h 30min" format
    Regex("(\\d+)\\s*h(?:our)?\\s*(\\d+)\\s*m(?:in)?").find(input)?.let { match ->
        val h = match.groupValues[1].toIntOrNull() ?: 0
        val m = match.groupValues[2].toIntOrNull() ?: 0
        return h * 60 + m
    }

    // Try "1:30:00" format
    Regex("(\\d+):(\\d{2}):(\\d{2})").find(clean)?.let { match ->
        val h = match.groupValues[1].toIntOrNull() ?: 0
        val m = match.groupValues[2].toIntOrNull() ?: 0
        return h * 60 + m
    }

    // Try "90min" format
    Regex("(\\d+)\\s*m(?:in)?").find(clean)?.let { match ->
        return match.groupValues[1].toIntOrNull()
    }

    return null
}

/**
 * Truncate a string to a max length, adding an ellipsis.
 */
fun truncate(text: String, maxLength: Int): String {
    if (text.length <= maxLength) return text
    return text.take(maxLength - 1) + "…"
}

/**
 * Check if a string is a valid URL.
 */
fun isValidUrl(url: String): Boolean {
    return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("magnet:") || url.startsWith("torrent://")
}

/**
 * Check if a string is a valid info hash (40 hex chars or 32 base32 chars).
 */
fun isValidInfoHash(hash: String): Boolean {
    return (hash.length == 40 && hash.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) ||
           (hash.length == 32 && hash.all { it in '2'..'7' || it.lowercaseChar() in 'a'..'z' })
}

/**
 * Extract the info hash from a magnet URI.
 */
fun extractInfoHashFromMagnet(magnet: String): String? {
    val marker = "btih:"
    val idx = magnet.indexOf(marker, ignoreCase = true)
    if (idx < 0) return null
    val start = idx + marker.length
    val end = magnet.indexOf('&', start).takeIf { it >= 0 } ?: magnet.length
    val hash = magnet.substring(start, end).trim()
    return hash.takeIf { isValidInfoHash(it) }
}

/**
 * Extract trackers from a magnet URI.
 */
fun extractTrackersFromMagnet(magnet: String): List<String> {
    return Regex("tr=([^&]+)").findAll(magnet)
        .map { java.net.URLDecoder.decode(it.groupValues[1], "UTF-8") }
        .toList()
}
