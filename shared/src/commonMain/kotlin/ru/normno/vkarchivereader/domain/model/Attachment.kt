package ru.normno.vkarchivereader.domain.model

/** Category of a message attachment, derived from the VK "description" label. */
enum class AttachmentType {
    PHOTO, VIDEO, AUDIO, FILE, STICKER, LINK, OTHER;

    companion object {
        /** Map the Russian label used in the archive to a type. */
        fun fromDescription(description: String): AttachmentType {
            val d = description.lowercase()
            return when {
                d.startsWith("фотограф") || d.startsWith("картин") -> PHOTO
                d.startsWith("видео") -> VIDEO
                d.startsWith("аудио") || d.startsWith("голосов") -> AUDIO
                d.startsWith("стикер") -> STICKER
                d.startsWith("ссылк") -> LINK
                d.startsWith("файл") || d.startsWith("документ") -> FILE
                else -> OTHER
            }
        }
    }
}

/** A single attachment inside a message. */
data class Attachment(
    val type: AttachmentType,
    val description: String,
    val url: String,
) {
    /** True for things Coil can render as an image in a grid. */
    val isImage: Boolean
        get() = (type == AttachmentType.PHOTO || type == AttachmentType.STICKER) &&
            url.substringBefore('?').let {
                it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) ||
                    it.endsWith(".png", true) || it.endsWith(".webp", true) ||
                    it.contains("/impg/") || it.contains("userapi.com")
            }
}
