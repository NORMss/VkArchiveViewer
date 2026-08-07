package ru.normno.vkarchivereader.domain.model

/** Sort options for the chat list. */
enum class ChatSortOrder(val label: String) {
    LAST_MESSAGE("По последнему сообщению"),
    MESSAGE_COUNT("По количеству сообщений"),
    MEDIA_COUNT("По количеству медиа"),
    TITLE("По названию (А-Я)"),
    OLDEST_FIRST("Сначала старые чаты");

    fun sort(items: List<ChatSummary>): List<ChatSummary> = when (this) {
        LAST_MESSAGE -> items.sortedByDescending { it.lastMessageDate?.sortKey ?: Long.MIN_VALUE }
        MESSAGE_COUNT -> items.sortedByDescending { it.messageCount }
        MEDIA_COUNT -> items.sortedByDescending { it.mediaCount }
        TITLE -> items.sortedBy { it.title.lowercase() }
        OLDEST_FIRST -> items.sortedBy { it.lastMessageDate?.sortKey ?: Long.MAX_VALUE }
    }
}
