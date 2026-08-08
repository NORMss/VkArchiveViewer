package ru.normno.vkarchivereader.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun rememberUrlOpener(): UrlOpener = remember {
    UrlOpener { url ->
        val nsUrl = NSURL.URLWithString(url) ?: return@UrlOpener
        val app = UIApplication.sharedApplication
        app.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }
}
