package ru.normno.vkarchivereader.domain.model

/** Kind of conversation, inferred from the peer id. */
enum class ChatKind { USER, GROUP, CONVERSATION }

/**
 * Lightweight summary of a chat shown in the chat list. Heavy data (the actual
 * messages) is loaded lazily per page when a chat is opened.
 */
data class ChatSummary(
    val peerId: String,
    val title: String,
    /** Relative path to the chat folder inside the archive, e.g. "messages/-12345". */
    val dirPath: String,
    val kind: ChatKind,
    val messageCount: Int,
    val mediaCount: Int,
    val lastMessageDate: MessageDate?,
    val lastMessagePreview: String,
    /** Number of `messagesN.html` pages in the chat folder. */
    val pageCount: Int,
) {
    companion object {
        fun kindOf(peerId: String): ChatKind {
            val n = peerId.toLongOrNull() ?: return ChatKind.GROUP
            return when {
                n < 0 -> ChatKind.GROUP
                n >= 2_000_000_000L -> ChatKind.CONVERSATION
                else -> ChatKind.USER
            }
        }
    }
}
