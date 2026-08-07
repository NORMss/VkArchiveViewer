package ru.normno.vkarchivereader

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "VkArchiveReader",
    ) {
        App()
    }
}