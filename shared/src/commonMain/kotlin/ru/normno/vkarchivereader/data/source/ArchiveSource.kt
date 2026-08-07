package ru.normno.vkarchivereader.data.source

/**
 * Read-only view over a loaded VK archive (a directory tree or an unpacked zip).
 * All paths are POSIX-style and relative to the archive root, which is the
 * folder containing `index.html` and the `messages/` directory.
 *
 * Implementations are platform-specific (a folder on desktop/Android, an
 * in-memory zip elsewhere) but the parser only depends on this interface.
 */
interface ArchiveSource {
    /** Human-readable name of the archive (file/folder name). */
    val displayName: String

    /** Whether [path] (file) exists in the archive. */
    suspend fun exists(path: String): Boolean

    /** Raw bytes of a file, or null if absent. */
    suspend fun readBytes(path: String): ByteArray?

    /** Names (single path segment) of immediate sub-directories under [path]. */
    suspend fun listDirs(path: String): List<String>
}
