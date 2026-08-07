package ru.normno.vkarchivereader.core

import android.content.Context
import java.io.File

/**
 * Holds the application [Context] so the cache store can find the app cache dir
 * and preferences. Set this once from the Android entry point (MainActivity).
 */
object AndroidAppContext {
    @Volatile
    var application: Context? = null
}

private const val PREFS = "vk_archive_reader"
private const val KEY = "maxCacheBytes"

actual object ImageCacheStore {
    private val context: Context? get() = AndroidAppContext.application

    private fun cacheDir(): File? =
        context?.let { File(it.cacheDir, "image_cache").apply { mkdirs() } }

    actual fun diskCacheDir(): String? = cacheDir()?.absolutePath

    actual fun maxBytes(): Long {
        val ctx = context ?: return DEFAULT_MAX_CACHE_BYTES
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY, DEFAULT_MAX_CACHE_BYTES)
    }

    actual fun setMaxBytes(value: Long) {
        val ctx = context ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY, value).apply()
    }

    actual fun clear() {
        cacheDir()?.listFiles()?.forEach { it.deleteRecursively() }
    }
}
