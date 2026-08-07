package ru.normno.vkarchivereader.core

/** 3 GB default on-device image cache limit. */
const val DEFAULT_MAX_CACHE_BYTES: Long = 3L * 1024 * 1024 * 1024
const val MIN_CACHE_BYTES: Long = 256L * 1024 * 1024 // 256 MB
const val MAX_CACHE_BYTES: Long = 20L * 1024 * 1024 * 1024 // 20 GB
const val BYTES_IN_GB: Long = 1024L * 1024 * 1024

/**
 * Cross-platform access to the on-device Coil image cache configuration.
 *
 * The disk cache lets media load instantly on a later launch. On the web there
 * is no app-managed disk cache ([diskCacheDir] returns null) — the browser's own
 * HTTP cache handles persistence — but the size preference is still stored.
 */
expect object ImageCacheStore {
    /** Directory for the Coil disk cache, or null when not supported (web). */
    fun diskCacheDir(): String?

    /** Persisted maximum cache size in bytes (default [DEFAULT_MAX_CACHE_BYTES]). */
    fun maxBytes(): Long

    /** Persist a new maximum cache size in bytes. Applied on next app launch. */
    fun setMaxBytes(value: Long)

    /** Delete everything currently cached on disk. */
    fun clear()
}
