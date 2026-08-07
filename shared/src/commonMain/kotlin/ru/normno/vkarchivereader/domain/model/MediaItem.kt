package ru.normno.vkarchivereader.domain.model

/** A media attachment together with where it came from (for the global gallery). */
data class MediaItem(
    val url: String,
    val type: AttachmentType,
    val chatPeerId: String,
    val chatTitle: String,
    val messageDate: MessageDate?,
)
