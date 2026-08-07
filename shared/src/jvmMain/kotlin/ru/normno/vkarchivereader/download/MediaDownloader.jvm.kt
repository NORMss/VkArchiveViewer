package ru.normno.vkarchivereader.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class JvmMediaDownloader : MediaDownloader {
    private val client = HttpClient(CIO)
    private val dir = File(System.getProperty("user.home"), "Downloads/VkArchiveReader")
        .apply { mkdirs() }

    override suspend fun download(url: String, fileName: String): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val bytes = client.get(url).body<ByteArray>()
                if (bytes.isEmpty()) return@withContext DownloadResult.FAILED
                File(dir, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")).writeBytes(bytes)
                DownloadResult.SAVED
            } catch (e: Throwable) {
                DownloadResult.FAILED
            }
        }
}

@Composable
actual fun rememberMediaDownloader(): MediaDownloader = remember { JvmMediaDownloader() }
