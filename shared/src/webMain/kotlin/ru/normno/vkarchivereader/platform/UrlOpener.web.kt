package ru.normno.vkarchivereader.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

@Composable
actual fun rememberUrlOpener(): UrlOpener = remember {
    UrlOpener { url -> window.open(url, "_blank") }
}
