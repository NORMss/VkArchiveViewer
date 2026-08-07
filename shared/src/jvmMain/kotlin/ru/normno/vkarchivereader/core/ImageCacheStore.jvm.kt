package ru.normno.vkarchivereader.core

import java.io.File
import java.util.Properties

private val appDir: File by lazy {
    File(System.getProperty("user.home"), ".vkarchivereader").apply { mkdirs() }
}
private val settingsFile: File by lazy { File(appDir, "settings.properties") }
private val cacheDir: File by lazy { File(appDir, "image_cache").apply { mkdirs() } }

actual object ImageCacheStore {
    actual fun diskCacheDir(): String? = cacheDir.absolutePath

    actual fun maxBytes(): Long {
        if (!settingsFile.isFile) return DEFAULT_MAX_CACHE_BYTES
        return runCatching {
            val props = Properties().apply { settingsFile.inputStream().use { load(it) } }
            props.getProperty("maxCacheBytes")?.toLong() ?: DEFAULT_MAX_CACHE_BYTES
        }.getOrDefault(DEFAULT_MAX_CACHE_BYTES)
    }

    actual fun setMaxBytes(value: Long) {
        runCatching {
            val props = Properties()
            if (settingsFile.isFile) settingsFile.inputStream().use { props.load(it) }
            props.setProperty("maxCacheBytes", value.toString())
            settingsFile.outputStream().use { props.store(it, "VK Archive Reader settings") }
        }
    }

    actual fun clear() {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
