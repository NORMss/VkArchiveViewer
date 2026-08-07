package ru.normno.vkarchivereader.core

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

private const val KEY = "vkArchiveReader.maxCacheBytes"

actual object ImageCacheStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    private fun cacheDirPath(): String? {
        val base = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: return null
        val dir = "$base/vk_image_cache"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        return dir
    }

    actual fun diskCacheDir(): String? = cacheDirPath()

    actual fun maxBytes(): Long {
        if (defaults.objectForKey(KEY) == null) return DEFAULT_MAX_CACHE_BYTES
        return defaults.integerForKey(KEY)
    }

    actual fun setMaxBytes(value: Long) {
        defaults.setInteger(value, KEY)
    }

    actual fun clear() {
        val dir = cacheDirPath() ?: return
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(dir, null) ?: return
        for (name in contents) {
            (name as? String)?.let { fm.removeItemAtPath("$dir/$it", null) }
        }
    }
}
