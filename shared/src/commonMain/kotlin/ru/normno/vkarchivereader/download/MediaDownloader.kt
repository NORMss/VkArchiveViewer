package ru.normno.vkarchivereader.download

import androidx.compose.runtime.Composable

enum class DownloadResult { SAVED, FAILED, UNSUPPORTED }

/** Downloads remote media to the device. Implemented per platform. */
interface MediaDownloader {
    /** Save one image (by URL) to the device. */
    suspend fun download(url: String, fileName: String): DownloadResult
}

@Composable
expect fun rememberMediaDownloader(): MediaDownloader

/** Build a safe-ish file name for a media URL, preserving the real extension. */
fun fileNameFor(url: String, index: Int, prefix: String = "vk"): String {
    val tail = url.substringBefore('?').substringAfterLast('/')
    val ext = tail.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it.isNotEmpty() && it.length in 1..5 && it.all(Char::isLetterOrDigit) }
        ?: "jpg" // Many VK photo URLs carry no extension; default to jpg.
    return "${prefix}_${index}.$ext"
}

/** Best-effort MIME type from a file name's extension. */
fun mimeTypeForName(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "ogg", "oga" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}

/** True for image MIME types, which go to the gallery rather than Downloads. */
fun isImageMime(mime: String): Boolean = mime.startsWith("image/")
