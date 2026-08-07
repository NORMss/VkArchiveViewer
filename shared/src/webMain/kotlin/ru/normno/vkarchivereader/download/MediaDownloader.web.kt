package ru.normno.vkarchivereader.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement

private class WebMediaDownloader : MediaDownloader {
    override suspend fun download(url: String, fileName: String): DownloadResult {
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.target = "_blank"
        // Cross-origin downloads may open the image in a new tab instead of
        // saving directly; the user can then save it. Same-origin saves directly.
        anchor.setAttribute("download", fileName)
        document.body?.appendChild(anchor)
        anchor.click()
        document.body?.removeChild(anchor)
        return DownloadResult.SAVED
    }
}

@Composable
actual fun rememberMediaDownloader(): MediaDownloader = remember { WebMediaDownloader() }
