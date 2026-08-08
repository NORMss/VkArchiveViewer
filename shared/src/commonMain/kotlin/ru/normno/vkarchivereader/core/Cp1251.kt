package ru.normno.vkarchivereader.core

/**
 * Pure-Kotlin Windows-1251 (cp1251) decoder.
 *
 * VK archive HTML files are encoded in windows-1251. There is no common-code
 * charset decoder in Kotlin Multiplatform, so we decode by hand. cp1251 is a
 * single-byte encoding: bytes 0x00..0x7F map to ASCII, 0xC0..0xFF map to the
 * contiguous Cyrillic block U+0410..U+044F, and 0x80..0xBF use the table below.
 */
object Cp1251 {

    // High-range mapping for bytes 0x80..0xBF (index = byte - 0x80).
    private val high: CharArray = charArrayOf(
        'Ђ', 'Ѓ', '‚', 'ѓ', '„', '…', '†', '‡', // 80..87
        '€', '‰', 'Љ', '‹', 'Њ', 'Ќ', 'Ћ', 'Џ', // 88..8F
        'ђ', '‘', '’', '“', '”', '•', '–', '—', // 90..97
        '�', '™', 'љ', '›', 'њ', 'ќ', 'ћ', 'џ', // 98..9F
        ' ', 'Ў', 'ў', 'Ј', '¤', 'Ґ', '¦', '§', // A0..A7
        'Ё', '©', 'Є', '«', '¬', '­', '®', 'Ї', // A8..AF
        '°', '±', 'І', 'і', 'ґ', 'µ', '¶', '·', // B0..B7
        'ё', '№', 'є', '»', 'ј', 'Ѕ', 'ѕ', 'ї', // B8..BF
    )

    fun decode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            val ch = when {
                v < 0x80 -> v.toChar()
                v < 0xC0 -> high[v - 0x80]
                else -> (0x0410 + (v - 0xC0)).toChar() // А..я
            }
            sb.append(ch)
        }
        return sb.toString()
    }

    // Reverse of [high] for the 0x80..0xBF range (char -> byte), built once.
    // The replacement char is skipped; the first mapping for a char wins.
    private val highReverse: Map<Char, Int> = buildMap {
        high.forEachIndexed { i, c -> if (c != '�' && c !in this) put(c, 0x80 + i) }
    }

    /**
     * Encode [text] back to windows-1251 bytes — the inverse of [decode]. Used to
     * synthesize archive-shaped HTML (e.g. the built-in demo archive). Characters
     * outside cp1251 (such as emoji) cannot be represented and become `?`; write
     * those as HTML numeric entities (`&#128512;`) instead, which stay ASCII.
     */
    fun encode(text: String): ByteArray {
        val out = ByteArray(text.length)
        for (i in text.indices) {
            val code = text[i].code
            out[i] = when {
                code < 0x80 -> code
                code in 0x0410..0x044F -> 0xC0 + (code - 0x0410) // А..я
                else -> highReverse[text[i]] ?: '?'.code
            }.toByte()
        }
        return out
    }
}
