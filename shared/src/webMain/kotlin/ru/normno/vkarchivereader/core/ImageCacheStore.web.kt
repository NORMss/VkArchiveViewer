package ru.normno.vkarchivereader.core

import kotlinx.browser.localStorage

private const val KEY = "vkArchiveReader.maxCacheBytes"

/**
 * On the web there is no app-managed disk cache (the browser's HTTP cache handles
 * persistence), so [diskCacheDir] is null. The size preference is still kept in
 * localStorage so the settings UI is consistent across launches.
 */
actual object ImageCacheStore {
    actual fun diskCacheDir(): String? = null

    actual fun maxBytes(): Long =
        localStorage.getItem(KEY)?.toLongOrNull() ?: DEFAULT_MAX_CACHE_BYTES

    actual fun setMaxBytes(value: Long) {
        localStorage.setItem(KEY, value.toString())
    }

    actual fun clear() {
        // The browser manages its own HTTP cache; nothing to clear here.
    }
}
