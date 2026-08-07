package ru.normno.vkarchivereader.data.source

import java.io.File
import java.util.zip.ZipInputStream

/** Archive backed by an extracted folder on disk. */
class DirectoryArchiveSource(private val root: File) : ArchiveSource {
    override val displayName: String = root.name

    override suspend fun exists(path: String): Boolean = File(root, path).isFile

    override suspend fun readBytes(path: String): ByteArray? =
        File(root, path).takeIf { it.isFile }?.readBytes()

    override suspend fun listDirs(path: String): List<String> =
        File(root, path).listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
}

/**
 * Archive backed by a .zip read fully into memory. The VK archive zip only
 * contains small HTML/CSS (media is remote), so this is cheap.
 */
class InMemoryArchiveSource(
    override val displayName: String,
    private val entries: Map<String, ByteArray>,
) : ArchiveSource {

    override suspend fun exists(path: String): Boolean = entries.containsKey(path)

    override suspend fun readBytes(path: String): ByteArray? = entries[path]

    override suspend fun listDirs(path: String): List<String> {
        val prefix = if (path.isEmpty()) "" else "$path/"
        return entries.keys.asSequence()
            .filter { it.startsWith(prefix) }
            .mapNotNull {
                val rest = it.removePrefix(prefix)
                val seg = rest.substringBefore('/')
                if (seg.isNotEmpty() && rest.contains('/')) seg else null
            }
            .distinct()
            .toList()
    }
}

internal fun zipToArchiveSource(zipFile: File): InMemoryArchiveSource =
    readZipEntries(zipFile.readBytes()).let { raw ->
        val prefix = archiveRootPrefix(raw.keys)
        val rebased = raw.entries
            .filter { it.key.startsWith(prefix) }
            .associate { it.key.removePrefix(prefix) to it.value }
        InMemoryArchiveSource(zipFile.name, rebased)
    }

internal fun zipBytesToArchiveSource(name: String, bytes: ByteArray): InMemoryArchiveSource {
    val raw = readZipEntries(bytes)
    val prefix = archiveRootPrefix(raw.keys)
    val rebased = raw.entries
        .filter { it.key.startsWith(prefix) }
        .associate { it.key.removePrefix(prefix) to it.value }
    return InMemoryArchiveSource(name, rebased)
}

private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    val out = HashMap<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                out[entry.name.replace('\\', '/')] = zis.readBytes()
            }
            entry = zis.nextEntry
        }
    }
    return out
}

/** The path prefix inside the zip that precedes the archive root (folder with messages/). */
private fun archiveRootPrefix(keys: Set<String>): String {
    val marker = "messages/index-messages.html"
    keys.firstOrNull { it.endsWith(marker) }?.let { return it.removeSuffix(marker) }
    keys.firstOrNull { it.endsWith("index.html") }?.let { return it.removeSuffix("index.html") }
    return ""
}

/** Resolve the archive root from a chosen file or directory. */
internal fun resolveDirectorySource(chosen: File): DirectoryArchiveSource {
    val dir = if (chosen.isDirectory) chosen else chosen.parentFile
    val root = when {
        File(dir, "messages/index-messages.html").isFile -> dir
        File(dir, "index-messages.html").isFile -> dir.parentFile // picked messages/ folder
        else -> dir
    }
    return DirectoryArchiveSource(root)
}
