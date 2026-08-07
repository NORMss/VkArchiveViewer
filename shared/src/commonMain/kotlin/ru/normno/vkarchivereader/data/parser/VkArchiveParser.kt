package ru.normno.vkarchivereader.data.parser

import ru.normno.vkarchivereader.core.Html
import ru.normno.vkarchivereader.domain.model.Attachment
import ru.normno.vkarchivereader.domain.model.AttachmentType
import ru.normno.vkarchivereader.domain.model.Message
import ru.normno.vkarchivereader.domain.model.MessageDate

/** A peer entry from `messages/index-messages.html`. */
data class PeerEntry(val peerId: String, val title: String, val firstPageHref: String)

/**
 * Pure (no IO) parsing of the VK archive HTML. See [VK_ARCHIVE_STRUCTURE.md].
 * Input strings must already be decoded from windows-1251.
 */
object VkArchiveParser {

    private val ownerRegex = Regex(""""user_id"\s*:\s*(\d+)""")

    // Note: `[\s\S]` is used instead of `.` with DOT_MATCHES_ALL because that
    // RegexOption is JVM-only and would not compile for JS/Wasm/Native.
    private val peerRegex = Regex(
        """<div class="message-peer--id">\s*<a href="([^"]+)">([\s\S]*?)</a>""",
    )

    private val messageRegex = Regex(
        """<div class="message" data-id="([^"]*)">([\s\S]*?)(?=<div class="message" data-id="|<div class="pagination|$)""",
    )

    private val headerRegex = Regex(
        """<div class="message__header">([\s\S]*?)</div>""",
    )

    private val authorRegex = Regex("""<a href="([^"]*)"[^>]*>([\s\S]*?)</a>""")

    private val attachmentRegex = Regex(
        """attachment__description">([\s\S]*?)</div>\s*<a class=['"]attachment__link['"] href=['"]([^'"]+)['"]""",
    )

    private val dateRegex = Regex("""(\d+)\s+([А-Яа-я]+)\s+(\d{4})\s+в\s+(\d+):(\d+):(\d+)""")

    private val months = mapOf(
        "янв" to 1, "фев" to 2, "мар" to 3, "апр" to 4, "май" to 5, "мая" to 5,
        "июн" to 6, "июл" to 7, "авг" to 8, "сен" to 9, "окт" to 10,
        "ноя" to 11, "дек" to 12,
    )

    /** Owner user id from the `<meta name="jd">` payload of index.html. */
    fun parseOwnerId(indexHtml: String): String? {
        val meta = Regex("""name="jd"\s+content="([^"]+)"""").find(indexHtml) ?: return null
        val json = decodeBase64ToString(meta.groupValues[1]) ?: return null
        return ownerRegex.find(json)?.groupValues?.get(1)
    }

    fun parsePeers(indexMessagesHtml: String): List<PeerEntry> =
        peerRegex.findAll(indexMessagesHtml).map { m ->
            val href = m.groupValues[1]
            val title = Html.toPlainText(m.groupValues[2]).ifBlank { href.substringBefore('/') }
            PeerEntry(
                peerId = href.substringBefore('/'),
                title = title,
                firstPageHref = href,
            )
        }.toList()

    /** Parse one `messagesN.html` page into messages (page order = newest first). */
    fun parsePage(pageHtml: String, ownerId: String?): List<Message> =
        messageRegex.findAll(pageHtml).map { m ->
            val id = m.groupValues[1]
            val body = m.groupValues[2]

            val headerHtml = headerRegex.find(body)?.groupValues?.get(1).orEmpty()
            val author = authorRegex.find(headerHtml)
            val authorLink = author?.groupValues?.get(1).orEmpty()
            val authorName = author?.let { Html.toPlainText(it.groupValues[2]) }.orEmpty()
            val dateText = headerHtml
                .let { if (author != null) it.substringAfter("</a>", it) else it }
                .trim().trimStart(',', ' ')
            val date = parseDate(Html.toPlainText(dateText))

            // Body content lives after the header's closing </div>.
            val afterHeader = body.substringAfter("message__header", "")
                .substringAfter("</div>", body)
            val textHtml = afterHeader.substringBefore("""<div class="kludges"""", afterHeader)
            val text = Html.toPlainText(textHtml)

            val attachments = attachmentRegex.findAll(body).map { a ->
                val desc = Html.toPlainText(a.groupValues[1])
                Attachment(
                    type = AttachmentType.fromDescription(desc),
                    description = desc,
                    url = Html.decodeEntities(a.groupValues[2]).trim(),
                )
            }.toList()

            Message(
                id = id,
                authorName = authorName,
                authorLink = authorLink,
                date = date,
                text = text,
                attachments = attachments,
                // VK does not render an author link for the archive owner's own
                // messages, so an empty author marks an outgoing message. (The
                // ownerId check stays as a fallback for other export shapes.)
                isOutgoing = authorLink.isBlank() ||
                    (ownerId != null && authorLink.contains("id$ownerId") && !authorLink.contains("club")),
            )
        }.toList()

    fun parseDate(text: String): MessageDate? {
        val m = dateRegex.find(text) ?: return null
        val (d, monStr, y, h, min, s) = m.destructured
        val month = months[monStr.take(3).lowercase()] ?: return null
        return MessageDate(
            year = y.toInt(), month = month, day = d.toInt(),
            hour = h.toInt(), minute = min.toInt(), second = s.toInt(),
            raw = m.value,
        )
    }

    // --- minimal standard base64 decoder (no opt-in APIs needed) ---
    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun decodeBase64ToString(input: String): String? {
        val clean = input.trim().trimEnd('=')
        val out = ArrayList<Byte>(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = B64.indexOf(c)
            if (v < 0) continue
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        if (out.isEmpty()) return null
        return out.toByteArray().decodeToString()
    }
}
