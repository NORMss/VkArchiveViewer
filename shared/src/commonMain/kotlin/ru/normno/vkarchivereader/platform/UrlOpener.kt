package ru.normno.vkarchivereader.platform

import androidx.compose.runtime.Composable

/**
 * Opens a remote URL with the device's default handler — the browser for links,
 * the video player for videos, the music player for audio, a document viewer for
 * files, and so on. Everything except photos (shown in the in-app viewer) is
 * delegated to the OS so each file type opens in its native app.
 */
fun interface UrlOpener {
    fun open(url: String)
}

@Composable
expect fun rememberUrlOpener(): UrlOpener
