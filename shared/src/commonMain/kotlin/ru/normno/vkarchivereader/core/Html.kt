package ru.normno.vkarchivereader.core

/**
 * Minimal HTML helpers for the small, well-formed snippets produced by the VK
 * archive exporter (we do not need a full HTML parser).
 */
object Html {

    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "laquo" to "«", "raquo" to "»", "mdash" to "—",
        "ndash" to "–", "hellip" to "…", "copy" to "©", "reg" to "®",
        "deg" to "°", "middot" to "·", "bull" to "•",
    )

    /** Decode HTML entities (`&#1234;`, `&#x1F600;`, `&amp;`, …) into plain text. */
    fun decodeEntities(input: String): String {
        if (input.indexOf('&') < 0) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '&') {
                val semi = input.indexOf(';', i + 1)
                if (semi in (i + 1)..(i + 12)) {
                    val body = input.substring(i + 1, semi)
                    val decoded = when {
                        body.startsWith("#x") || body.startsWith("#X") ->
                            body.substring(2).toIntOrNull(16)?.let { codePoint(it) }
                        body.startsWith("#") ->
                            body.substring(1).toIntOrNull()?.let { codePoint(it) }
                        else -> namedEntities[body]
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /** Replace `<br>` variants with newlines, strip remaining tags, decode entities. */
    fun toPlainText(html: String): String {
        val withBreaks = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        val noTags = stripTags(withBreaks)
        return decodeEntities(noTags).trim()
    }

    fun stripTags(html: String): String = html.replace(Regex("<[^>]*>"), "")

    private fun codePoint(cp: Int): String =
        if (cp in 0..0xFFFF) cp.toChar().toString()
        else {
            // Surrogate pair for astral code points (emoji).
            val v = cp - 0x10000
            charArrayOf(
                (0xD800 + (v shr 10)).toChar(),
                (0xDC00 + (v and 0x3FF)).toChar(),
            ).concatToString()
        }
}
