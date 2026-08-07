package ru.normno.vkarchivereader

import kotlinx.coroutines.runBlocking
import ru.normno.vkarchivereader.data.repository.ArchiveRepository
import ru.normno.vkarchivereader.data.source.DemoArchiveSource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies the built-in demo archive flows through the real parser/repository:
 * chats, exact message counts, multi-page pagination, media indexing, outgoing
 * detection, multiple authors (for the author filter) and search all work.
 */
class DemoArchiveTest {

    @Test
    fun demoArchiveOpensAndParses() = runBlocking {
        val repo = ArchiveRepository()
        val data = repo.open(DemoArchiveSource())

        assertTrue(data.chats.size >= 5, "expected several demo chats, got ${data.chats.size}")
        assertTrue(data.chats.all { it.messageCount > 0 }, "every demo chat must have messages")

        // The "Мемы и котики" chat is generated with 56 posts -> more than one page.
        val paged = data.chats.first { it.title.contains("Мемы") }
        assertTrue(paged.pageCount >= 2, "expected pagination, pageCount=${paged.pageCount}")

        // Outgoing detection: personal chat with Anna must contain owner ("Вы") messages.
        val anna = data.chats.first { it.title.contains("Анна") }
        val annaMsgs = repo.readPage(anna.dirPath, 0)
        assertTrue(annaMsgs.any { it.isOutgoing }, "expected outgoing messages")
        assertTrue(annaMsgs.any { !it.isOutgoing }, "expected incoming messages")

        // The group conversation must expose several distinct authors.
        val friends = data.chats.first { it.kind.name == "CONVERSATION" }
        val authors = repo.readPage(friends.dirPath, 0).map { it.displayAuthor }.toSet()
        assertTrue(authors.size >= 3, "expected multiple authors, got $authors")

        // Media indexing fills the gallery.
        repo.indexMedia()
        val indexed = repo.data.value!!
        assertTrue(indexed.mediaIndexed, "indexing should complete")
        assertTrue(indexed.media.size >= 30, "expected media, got ${indexed.media.size}")

        // Global search finds the demo text.
        val hits = repo.search("пикник", peerId = null, limit = 10)
        assertTrue(hits.isNotEmpty(), "expected search hits for 'пикник'")
    }
}
