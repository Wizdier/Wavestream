package com.wavestream.features.player.subtitles

/**
 * Subtitle language helper — converts between language names and IETF BCP 47 tags.
 *
 * Mirrors CloudStream's `SubtitleHelper`.
 *
 * Used to:
 *   - Match subtitle files to the user's preferred language
 *   - Display language names in the subtitle picker
 *   - Convert between ISO 639-1 (2-letter), ISO 639-2 (3-letter), and full names
 */
object SubtitleHelper {

    private val languageMap = mapOf(
        "en" to "English", "eng" to "English",
        "es" to "Spanish", "spa" to "Spanish",
        "fr" to "French", "fra" to "French",
        "de" to "German", "deu" to "German",
        "it" to "Italian", "ita" to "Italian",
        "pt" to "Portuguese", "por" to "Portuguese",
        "pt-BR" to "Portuguese (Brazil)",
        "ru" to "Russian", "rus" to "Russian",
        "ja" to "Japanese", "jpn" to "Japanese",
        "ko" to "Korean", "kor" to "Korean",
        "zh" to "Chinese", "zho" to "Chinese", "chi" to "Chinese",
        "zh-CN" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
        "ar" to "Arabic", "ara" to "Arabic",
        "hi" to "Hindi", "hin" to "Hindi",
        "tr" to "Turkish", "tur" to "Turkish",
        "pl" to "Polish", "pol" to "Polish",
        "nl" to "Dutch", "nld" to "Dutch",
        "sv" to "Swedish", "swe" to "Swedish",
        "da" to "Danish", "dan" to "Danish",
        "fi" to "Finnish", "fin" to "Finnish",
        "no" to "Norwegian", "nor" to "Norwegian",
        "cs" to "Czech", "ces" to "Czech", "cze" to "Czech",
        "sk" to "Slovak", "slk" to "Slovak", "slo" to "Slovak",
        "hu" to "Hungarian", "hun" to "Hungarian",
        "ro" to "Romanian", "ron" to "Romanian", "rum" to "Romanian",
        "bg" to "Bulgarian", "bul" to "Bulgarian",
        "hr" to "Croatian", "hrv" to "Croatian",
        "sr" to "Serbian", "srp" to "Serbian",
        "sl" to "Slovenian", "slv" to "Slovenian",
        "el" to "Greek", "ell" to "Greek", "gre" to "Greek",
        "he" to "Hebrew", "heb" to "Hebrew",
        "th" to "Thai", "tha" to "Thai",
        "vi" to "Vietnamese", "vie" to "Vietnamese",
        "id" to "Indonesian", "ind" to "Indonesian",
        "ms" to "Malay", "msa" to "Malay", "may" to "Malay",
        "uk" to "Ukrainian", "ukr" to "Ukrainian",
        "fa" to "Persian", "fas" to "Persian", "per" to "Persian",
        "bn" to "Bengali", "ben" to "Bengali",
        "ta" to "Tamil", "tam" to "Tamil",
        "te" to "Telugu", "tel" to "Telugu",
        "ml" to "Malayalam", "mal" to "Malayalam",
        "kn" to "Kannada", "kan" to "Kannada",
        "mr" to "Marathi", "mar" to "Marathi",
        "gu" to "Gujarati", "guj" to "Gujarati",
        "pa" to "Punjabi", "pan" to "Punjabi",
        "ur" to "Urdu", "urd" to "Urdu",
        "lt" to "Lithuanian", "lit" to "Lithuanian",
        "lv" to "Latvian", "lav" to "Latvian",
        "et" to "Estonian", "est" to "Estonian",
        "is" to "Icelandic", "isl" to "Icelandic", "ice" to "Icelandic",
        "ga" to "Irish", "gle" to "Irish",
        "cy" to "Welsh", "cym" to "Welsh", "wel" to "Welsh",
        "eu" to "Basque", "eus" to "Basque", "baq" to "Basque",
        "ca" to "Catalan", "cat" to "Catalan",
        "gl" to "Galician", "glg" to "Galician",
        "af" to "Afrikaans", "afr" to "Afrikaans",
        "sw" to "Swahili", "swa" to "Swahili",
        "am" to "Amharic", "amh" to "Amharic",
        "ne" to "Nepali", "nep" to "Nepali",
        "si" to "Sinhala", "sin" to "Sinhala",
        "my" to "Burmese", "mya" to "Burmese", "bur" to "Burmese",
        "km" to "Khmer", "khm" to "Khmer",
        "lo" to "Lao", "lao" to "Lao",
        "ka" to "Georgian", "kat" to "Georgian", "geo" to "Georgian",
        "hy" to "Armenian", "hye" to "Armenian", "arm" to "Armenian",
        "az" to "Azerbaijani", "aze" to "Azerbaijani",
        "kk" to "Kazakh", "kaz" to "Kazakh",
        "uz" to "Uzbek", "uzb" to "Uzbek",
        "ky" to "Kyrgyz", "kir" to "Kyrgyz",
        "tg" to "Tajik", "tgk" to "Tajik",
        "tk" to "Turkmen", "tuk" to "Turkmen",
        "mn" to "Mongolian", "mon" to "Mongolian",
    )

    private val reverseMap = languageMap.entries.associate { (code, name) -> name.lowercase() to code }

    /**
     * Convert a language code to a language name.
     */
    fun fromCodeToLanguage(code: String?): String? {
        if (code == null) return null
        val normalized = code.trim().lowercase()
        return languageMap[normalized] ?: languageMap[normalized.substringBefore("-")]
    }

    /**
     * Convert a language name to a language code.
     */
    fun fromLanguageToCode(language: String?): String? {
        if (language == null) return null
        return reverseMap[language.trim().lowercase()]
    }

    /**
     * Convert a language code to an IETF BCP 47 language tag.
     * E.g. "en" → "en", "pt-BR" → "pt-BR", "eng" → "en"
     */
    fun fromCodeToLangTagIETF(code: String?): String? {
        if (code == null) return null
        val normalized = code.trim()
        // Already a valid IETF tag
        if (normalized.contains("-")) return normalized
        // 2-letter code is already IETF
        if (normalized.length == 2) return normalized
        // 3-letter code → convert to 2-letter
        val lower = normalized.lowercase()
        return when (lower) {
            "eng" -> "en"
            "spa" -> "es"
            "fra" -> "fr"
            "deu" -> "de"
            "ita" -> "it"
            "por" -> "pt"
            "rus" -> "ru"
            "jpn" -> "ja"
            "kor" -> "ko"
            "zho", "chi" -> "zh"
            "ara" -> "ar"
            "hin" -> "hi"
            "tur" -> "tr"
            "pol" -> "pl"
            "nld" -> "nl"
            "swe" -> "sv"
            "dan" -> "da"
            "fin" -> "fi"
            "nor" -> "no"
            "ces", "cze" -> "cs"
            "slk", "slo" -> "sk"
            "hun" -> "hu"
            "ron", "rum" -> "ro"
            "bul" -> "bg"
            "hrv" -> "hr"
            "srp" -> "sr"
            "slv" -> "sl"
            "ell", "gre" -> "el"
            "heb" -> "he"
            "tha" -> "th"
            "vie" -> "vi"
            "ind" -> "id"
            "msa", "may" -> "ms"
            "ukr" -> "uk"
            "fas", "per" -> "fa"
            else -> normalized
        }
    }

    /**
     * Convert a language name to an IETF BCP 47 language tag.
     */
    fun fromLanguageToTagIETF(language: String?, preferAmerican: Boolean = false): String? {
        val code = fromLanguageToCode(language) ?: return null
        val tag = fromCodeToLangTagIETF(code) ?: return null
        // For English, prefer en-US if American English
        if (tag == "en" && preferAmerican) return "en-US"
        return tag
    }

    /**
     * Check if two language codes refer to the same language.
     */
    fun isSameLanguage(code1: String?, code2: String?): Boolean {
        if (code1 == null || code2 == null) return false
        val tag1 = fromCodeToLangTagIETF(code1)?.lowercase()
        val tag2 = fromCodeToLangTagIETF(code2)?.lowercase()
        if (tag1 == null || tag2 == null) return false
        // Compare base language (before the -)
        return tag1.substringBefore("-") == tag2.substringBefore("-")
    }

    /**
     * Get all supported languages.
     */
    fun getAllLanguages(): Map<String, String> = languageMap.toMap()
}
