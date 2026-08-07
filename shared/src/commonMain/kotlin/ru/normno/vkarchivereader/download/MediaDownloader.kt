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

/** Build a safe-ish file name for a media URL. */
fun fileNameFor(url: String, index: Int, prefix: String = "vk"): String {
    val tail = url.substringBefore('?').substringAfterLast('/').take(40)
    val ext = when {
        tail.endsWith(".png", true) -> "png"
        tail.endsWith(".webp", true) -> "webp"
        else -> "jpg"
    }
    return "${prefix}_${index}.$ext"
}
