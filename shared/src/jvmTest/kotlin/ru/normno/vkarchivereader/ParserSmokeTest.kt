package ru.normno.vkarchivereader

import kotlinx.coroutines.runBlocking
import ru.normno.vkarchivereader.data.repository.ArchiveRepository
import ru.normno.vkarchivereader.data.source.DirectoryArchiveSource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Smoke test against the real exported archive (if present next to the project).
 * Skips silently when the archive folder is not available.
 */
class ParserSmokeTest {

    private fun findArchiveDir(): File? {
        var dir: File? = File(".").absoluteFile
        repeat(6) {
            dir ?: return null
            val candidate = File(dir, "Archive")
            if (File(candidate, "messages/index-messages.html").isFile) return candidate
            dir = dir!!.parentFile
        }
        return null
    }

    @Test
    fun parsesRealArchive() = runBlocking {
        val archiveDir = findArchiveDir()
        if (archiveDir == null) {
            println("[smoke] Archive not found near ${File(".").absolutePath} — skipping")
            return@runBlocking
        }

        val repo = ArchiveRepository()

        // Phase 1: light scan — should be fast and have exact message counts but no media yet.
        val t0 = System.currentTimeMillis()
        val light = repo.open(DirectoryArchiveSource(archiveDir))
        val lightMs = System.currentTimeMillis() - t0
        println("[smoke] light open: chats=${light.chats.size} media=${light.media.size} owner=${light.ownerId} in ${lightMs}ms")
        assertTrue(light.chats.isNotEmpty(), "expected at least one chat")
        assertTrue(light.media.isEmpty(), "light scan must not gather media")
        assertTrue(light.chats.any { it.messageCount > 0 }, "expected exact message counts")

        val sample = light.chats.maxByOrNull { it.messageCount }!!
        println("[smoke] biggest chat: '${sample.title}' msgs=${sample.messageCount} pages=${sample.pageCount} last=${sample.lastMessageDate?.raw}")

        val page = repo.readPage(sample.dirPath, 0)
        assertTrue(page.isNotEmpty(), "expected to read first page of biggest chat")
        val first = page.first()
        println("[smoke] sample message: outgoing=${first.isOutgoing} author='${first.authorName}' text='${first.text.take(50)}' attachments=${first.attachments.size}")

        // Phase 2: background media indexing.
        val t1 = System.currentTimeMillis()
        repo.indexMedia()
        val indexed = repo.data.value!!
        println("[smoke] indexed: media=${indexed.media.size} mediaIndexed=${indexed.mediaIndexed} in ${System.currentTimeMillis() - t1}ms")
        assertTrue(indexed.mediaIndexed, "indexing should complete")
        assertTrue(indexed.media.isNotEmpty(), "expected media after indexing")

        val hits = repo.search("а", peerId = sample.peerId, limit = 3)
        println("[smoke] in-chat search hits for 'а': ${hits.size}")
    }
}
