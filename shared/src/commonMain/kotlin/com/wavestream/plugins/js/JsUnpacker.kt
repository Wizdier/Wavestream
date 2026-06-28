package com.wavestream.plugins.js

/**
 * JavaScript unpacker — decodes packed/obfuscated JavaScript.
 *
 * Many video hosting sites use Dean Edwards' packer to obfuscate their
 * JavaScript code that constructs video URLs. This unpacker decodes the
 * packed code back to readable JavaScript.
 *
 * Mirrors CloudStream's `JsUnpacker.kt`.
 *
 * Packed format looks like:
 *   eval(function(p,a,c,k,e,d){...}('packed_string', base, count, 'k0|k1|k2|...', 0, {}))
 */
object JsUnpacker {

    private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,d\)\)\{.*?\}\([^)]+\)""")

    /**
     * Find packed JavaScript in a string.
     */
    fun getPacked(source: String): String? {
        return packedRegex.find(source)?.value
    }

    /**
     * Unpack packed JavaScript.
     *
     * @param packedSource The packed JS string (starting with `eval(function(p,a,c,k,e,d){...}`)
     * @return The unpacked JavaScript, or null if unpacking failed
     */
    fun unpack(packedSource: String): String? {
        val match = Regex(
            """\}\('([^']+)',(\d+),(\d+),'([^']+)'\[.\.\.\]\)"""
        ).find(packedSource) ?: return null

        val payload = match.groupValues[1]
        val base = match.groupValues[2].toInt()
        val count = match.groupValues[3].toInt()
        val keywords = match.groupValues[4].split("|")

        return unpackPayload(payload, base, count, keywords)
    }

    /**
     * Unpack the payload using the keyword list.
     */
    private fun unpackPayload(payload: String, base: Int, count: Int, keywords: List<String>): String {
        var result = payload
        // Replace each keyword token with its corresponding value
        for (i in count - 1 downTo 0) {
            val token = encode(i, base)
            val value = if (i < keywords.size) keywords[i] else ""
            if (value.isNotEmpty()) {
                result = result.replace("\\b$token\\b".toRegex(), value)
            }
        }
        return result
    }

    /**
     * Encode a number in the given base (Dean Edwards' encoding).
     */
    private fun encode(number: Int, base: Int): String {
        if (number < base) return charset[number].toString()
        val sb = StringBuilder()
        var n = number
        while (n > 0) {
            sb.insert(0, charset[n % base])
            n /= base
        }
        return sb.toString()
    }

    private val charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()

    /**
     * Find and unpack packed JavaScript in a source string.
     */
    fun unpackFromSource(source: String): String? {
        val packed = getPacked(source) ?: return null
        return unpack(packed)
    }
}

/**
 * JavaScript hunter — finds JavaScript variable assignments in HTML.
 * Used to extract video URLs that are assigned to JS variables.
 *
 * Mirrors CloudStream's `JsHunter.kt`.
 */
object JsHunter {

    /**
     * Find all URL assignments in JavaScript source.
     *
     * Looks for patterns like:
     *   var url = "https://..."
     *   var src = 'https://...'
     *   sources = [{file: "https://..."}]
     */
    fun findUrls(jsSource: String): List<String> {
        val urls = mutableSetOf<String>()

        // var x = "https://..."
        Regex("""var\s+\w+\s*=\s*["'](https?://[^"']+)["']""").findAll(jsSource).forEach {
            urls.add(it.groupValues[1])
        }

        // {file: "https://..."}
        Regex("""file\s*:\s*["'](https?://[^"']+)["']""").findAll(jsSource).forEach {
            urls.add(it.groupValues[1])
        }

        // src: "https://..."
        Regex("""src\s*:\s*["'](https?://[^"']+)["']""").findAll(jsSource).forEach {
            urls.add(it.groupValues[1])
        }

        // Direct URLs in strings
        Regex("""["'](https?://[^"']+\.(?:mp4|m3u8|mkv|webm)[^"']*)["']""").findAll(jsSource).forEach {
            urls.add(it.groupValues[1])
        }

        return urls.toList()
    }

    /**
     * Find a specific variable's value.
     */
    fun findVariable(jsSource: String, varName: String): String? {
        val regex = Regex("""var\s+$varName\s*=\s*["']([^"']+)["']""")
        return regex.find(jsSource)?.groupValues?.get(1)
    }

    /**
     * Find all variable assignments.
     */
    fun findAllVariables(jsSource: String): Map<String, String> {
        val vars = mutableMapOf<String, String>()
        Regex("""var\s+(\w+)\s*=\s*["']([^"']+)["']""").findAll(jsSource).forEach {
            vars[it.groupValues[1]] = it.groupValues[2]
        }
        return vars
    }
}
