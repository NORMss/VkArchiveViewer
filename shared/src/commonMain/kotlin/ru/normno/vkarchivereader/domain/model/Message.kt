package ru.normno.vkarchivereader.domain.model

/**
 * Parsed date of a message. Stored as components plus a comparable [sortKey]
 * so we can order messages without a multiplatform date library.
 */
data class MessageDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val raw: String,
) {
    /** Monotonic key (yyyymmddHHMMSS) for sorting; bigger = newer. */
    val sortKey: Long =
        ((((year.toLong() * 100 + month) * 100 + day) * 100 + hour) * 100 + minute) * 100 + second

    override fun toString(): String = raw
}

data class Message(
    val id: String,
    val authorName: String,
    val authorLink: String,
    val date: MessageDate?,
    val text: String,
    val attachments: List<Attachment>,
    /** True if sent by the archive owner. */
    val isOutgoing: Boolean,
) {
    val imageAttachments: List<Attachment> get() = attachments.filter { it.isImage }

    /**
     * Author label for grouping/filtering and display. The archive owner's own
     * messages carry no author name, so they show up as "Вы".
     */
    val displayAuthor: String get() = if (isOutgoing) "Вы" else authorName.ifBlank { "Вы" }
}
