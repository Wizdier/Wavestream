package com.wavestream.features.player.subtitles

import kotlinx.serialization.Serializable

/**
 * Subtitle cue — a single subtitle entry with start/end time and text.
 */
@Serializable
data class SubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
)

enum class SubtitleFormat {
    SRT, VTT, ASS, SSA, TXT, UNKNOWN;

    companion object {
        fun fromFileExtension(ext: String): SubtitleFormat = when (ext.lowercase()) {
            "srt" -> SRT
            "vtt" -> VTT
            "ass" -> ASS
            "ssa" -> SSA
            "txt" -> TXT
            else -> UNKNOWN
        }
    }
}

/**
 * Subtitle parser — parses SRT/VTT/ASS files into SubtitleCues.
 * Mirrors CloudStream's CustomSubripParser.
 */
object SubtitleParser {

    fun parse(content: String, format: SubtitleFormat): List<SubtitleCue> = when (format) {
        SubtitleFormat.SRT -> parseSrt(content)
        SubtitleFormat.VTT -> parseVtt(content)
        SubtitleFormat.ASS, SubtitleFormat.SSA -> parseAss(content)
        SubtitleFormat.TXT -> parseTxt(content)
        SubtitleFormat.UNKNOWN -> emptyList()
    }

    fun parseSrt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.replace("\r\n", "\n").trim().split("\n\n")
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.size < 2) continue
            val timecodeLine = if (lines[0].toIntOrNull() != null) lines[1] else lines[0]
            val textStartIdx = if (lines[0].toIntOrNull() != null) 2 else 1
            val match = Regex("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})").find(timecodeLine) ?: continue
            val (h1, m1, s1, ms1, h2, m2, s2, ms2) = match.destructured
            val start = (h1.toLong() * 3600 + m1.toLong() * 60 + s1.toLong()) * 1000 + ms1.toLong()
            val end = (h2.toLong() * 3600 + m2.toLong() * 60 + s2.toLong()) * 1000 + ms2.toLong()
            val text = lines.drop(textStartIdx).joinToString("\n")
            cues.add(SubtitleCue(start, end, text))
        }
        return cues
    }

    fun parseVtt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val body = content.replace("\r\n", "\n").trim().removePrefix("WEBVTT").trim()
        val blocks = body.split("\n\n")
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val timecodeIdx = lines.indexOfFirst { it.contains("-->") }
            if (timecodeIdx == -1) continue
            val match = Regex("(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})").find(lines[timecodeIdx]) ?: continue
            val (h1, m1, s1, ms1, h2, m2, s2, ms2) = match.destructured
            val start = (h1.toLong() * 3600 + m1.toLong() * 60 + s1.toLong()) * 1000 + ms1.toLong()
            val end = (h2.toLong() * 3600 + m2.toLong() * 60 + s2.toLong()) * 1000 + ms2.toLong()
            val text = lines.drop(timecodeIdx + 1).joinToString("\n")
            cues.add(SubtitleCue(start, end, text))
        }
        return cues
    }

    fun parseAss(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.replace("\r\n", "\n").lines()
        val eventsIdx = lines.indexOfFirst { it.trim() == "[Events]" }
        if (eventsIdx == -1) return emptyList()
        val formatLine = lines.drop(eventsIdx + 1).firstOrNull { it.startsWith("Format:") } ?: return emptyList()
        val fields = formatLine.removePrefix("Format:").split(",").map { it.trim() }
        val startIdx = fields.indexOf("Start")
        val endIdx = fields.indexOf("End")
        val textIdx = fields.indexOf("Text")
        if (startIdx == -1 || endIdx == -1 || textIdx == -1) return emptyList()
        for (line in lines.drop(eventsIdx + 1)) {
            if (!line.startsWith("Dialogue:")) continue
            val parts = line.removePrefix("Dialogue:").split(",")
            if (parts.size <= maxOf(startIdx, endIdx, textIdx)) continue
            val start = parseAssTime(parts[startIdx].trim())
            val end = parseAssTime(parts[endIdx].trim())
            val text = parts.drop(textIdx).joinToString(",")
                .replace(Regex("\\{[^}]*}"), "")
                .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
                .trim()
            if (text.isNotBlank()) cues.add(SubtitleCue(start, end, text))
        }
        return cues
    }

    private fun parseAssTime(tc: String): Long {
        val m = Regex("(\\d+):(\\d{2}):(\\d{2})\\.(\\d{1,3})").find(tc) ?: return 0L
        val (h, mi, s, ms) = m.destructured
        return (h.toLong() * 3600 + mi.toLong() * 60 + s.toLong()) * 1000 + ms.padEnd(3, '0').toLong()
    }

    fun parseTxt(content: String): List<SubtitleCue> {
        var t = 0L
        return content.replace("\r\n", "\n").split("\n").filter { it.isNotBlank() }.map {
            val end = t + 2000
            val cue = SubtitleCue(t, end, it)
            t = end
            cue
        }
    }
}
