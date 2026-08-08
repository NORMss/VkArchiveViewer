package ru.normno.vkarchivereader.presentation.navigation

import ru.normno.vkarchivereader.domain.model.ChatSummary
import ru.normno.vkarchivereader.domain.model.MediaItem

/** Simple state-based navigation (no nav library needed). */
sealed interface Screen {
    data object ChatList : Screen
    data class Conversation(val chat: ChatSummary, val targetMessageId: String? = null) : Screen
    data class MediaGallery(val peerId: String?, val chatTitle: String?) : Screen
    data class FaceGroups(val images: List<MediaItem>, val archiveId: String) : Screen
}
