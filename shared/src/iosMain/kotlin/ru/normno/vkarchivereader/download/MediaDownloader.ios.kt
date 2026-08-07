package ru.normno.vkarchivereader.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class IosMediaDownloader : MediaDownloader {
    override suspend fun download(url: String, fileName: String): DownloadResult =
        DownloadResult.UNSUPPORTED
}

@Composable
actual fun rememberMediaDownloader(): MediaDownloader = remember { IosMediaDownloader() }
