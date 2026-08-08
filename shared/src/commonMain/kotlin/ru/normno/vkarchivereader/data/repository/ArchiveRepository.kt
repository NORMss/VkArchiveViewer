package ru.normno.vkarchivereader.data.repository

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import ru.normno.vkarchivereader.core.Cp1251
import ru.normno.vkarchivereader.data.parser.VkArchiveParser
import ru.normno.vkarchivereader.data.source.ArchiveSource
import ru.normno.vkarchivereader.domain.model.ChatSummary
import ru.normno.vkarchivereader.domain.model.MediaItem
import ru.normno.vkarchivereader.domain.model.Message

/** Everything parsed from an opened archive. */
data class ArchiveData(
    val displayName: String,
    val ownerId: String?,
    val chats: List<ChatSummary>,
    val media: List<MediaItem>,
    /** True once the background media index has finished. */
    val mediaIndexed: Boolean = false,
)

data class SearchHit(
    val peerId: String,
    val chatTitle: String,
    val message: Message,
)

/**
 * Result of loading every message of a chat; [truncated] is true if the message
 * cap was hit. [authorCounts] (display author → message count) is collected over
 * *all* pages regardless of the cap, so the participant list stays complete even
 * for chats too big to hold in memory.
 */
data class LoadedMessages(
    val messages: List<Message>,
    val truncated: Boolean,
    val authorCounts: Map<String, Int> = emptyMap(),
)

/**
 * Reads and parses a VK archive on top of an [ArchiveSource].
 *
 * Loading is two-phase to keep the UI responsive even for huge archives:
 *  1. [open] — a *light* scan (2 page reads per chat) that produces the chat list
 *     with exact message counts, previews and dates almost instantly.
 *  2. [indexMedia] — a background pass over every page that fills in media counts
 *     and the global media list, yielding cooperatively so it never blocks the UI.
 */
class ArchiveRepository {

    private var source: ArchiveSource? = null
    private var ownerId: String? = null
    private var titles: Map<String, String> = emptyMap()

    private val _data = MutableStateFlow<ArchiveData?>(null)
    /** The currently opened archive, or null if none is loaded. */
    val data: StateFlow<ArchiveData?> = _data.asStateFlow()

    companion object {
        const val PAGE_SIZE = 50
        private const val MESSAGES_DIR = "messages"
        private const val YIELD_EVERY_PAGES = 16
        private const val PUBLISH_EVERY_CHATS = 20
    }

    /**
     * Light scan: chat list with exact message counts, previews and dates.
     * Media counts are 0 here and filled in later by [indexMedia].
     * [onProgress] is `(processedChats, totalChats)`.
     */
    suspend fun open(
        src: ArchiveSource,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): ArchiveData {
        source = src
        ownerId = readText("index.html")?.let { VkArchiveParser.parseOwnerId(it) }

        val indexHtml = readText("$MESSAGES_DIR/index-messages.html")
            ?: error("Не найден messages/index-messages.html — это не похоже на архив VK")
        val peers = VkArchiveParser.parsePeers(indexHtml)
        titles = peers.associate { it.peerId to it.title }

        val chats = ArrayList<ChatSummary>(peers.size)
        peers.forEachIndexed { idx, peer ->
            currentCoroutineContext().ensureActive()
            val dir = "$MESSAGES_DIR/${peer.peerId}"
            val pageCount = countPages(dir)

            val firstPage = if (pageCount > 0) readPage(dir, 0) else emptyList()
            val newest = firstPage.firstOrNull()
            // Every non-last page holds exactly PAGE_SIZE messages; only the last
            // page can be shorter — so one extra read gives an exact total.
            val lastPageSize = when {
                pageCount <= 1 -> firstPage.size
                else -> readPage(dir, pageCount - 1).size
            }
            val messageCount =
                if (pageCount == 0) 0 else (pageCount - 1) * PAGE_SIZE + lastPageSize

            chats.add(
                ChatSummary(
                    peerId = peer.peerId,
                    title = peer.title,
                    dirPath = dir,
                    kind = ChatSummary.kindOf(peer.peerId),
                    messageCount = messageCount,
                    mediaCount = 0,
                    lastMessageDate = newest?.date,
                    lastMessagePreview = newest?.text?.ifBlank {
                        newest.attachments.firstOrNull()?.description ?: ""
                    } ?: "",
                    pageCount = pageCount,
                )
            )
            onProgress(idx + 1, peers.size)
        }

        return ArchiveData(
            displayName = src.displayName,
            ownerId = ownerId,
            chats = chats,
            media = emptyList(),
            mediaIndexed = false,
        ).also { _data.value = it }
    }

    /**
     * Background pass that fills in per-chat media counts and the global media
     * list, publishing partial results to [data] as it goes. Cooperative: yields
     * regularly and honours cancellation.
     */
    suspend fun indexMedia(onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        val base = _data.value ?: return
        val chats = base.chats.toMutableList()
        val media = ArrayList<MediaItem>()

        base.chats.forEachIndexed { idx, chat ->
            currentCoroutineContext().ensureActive()
            var mediaCount = 0
            for (page in 0 until chat.pageCount) {
                val msgs = readPage(chat.dirPath, page)
                for (msg in msgs) {
                    for (att in msg.attachments) {
                        if (att.isImage || att.type.name == "VIDEO") {
                            media.add(
                                MediaItem(
                                    url = att.url,
                                    type = att.type,
                                    chatPeerId = chat.peerId,
                                    chatTitle = chat.title,
                                    messageDate = msg.date,
                                )
                            )
                        }
                        if (att.isImage) mediaCount++
                    }
                }
                if (page % YIELD_EVERY_PAGES == 0) yield()
            }
            chats[idx] = chat.copy(mediaCount = mediaCount)

            if (idx % PUBLISH_EVERY_CHATS == 0) {
                _data.value = base.copy(chats = chats.toList(), media = media.toList())
            }
            onProgress(idx + 1, base.chats.size)
        }

        _data.value = base.copy(
            chats = chats.toList(),
            media = media.toList(),
            mediaIndexed = true,
        )
    }

    fun close() {
        source = null
        ownerId = null
        titles = emptyMap()
        _data.value = null
    }

    /** Read a single message page (0-based) of a chat. */
    suspend fun readPage(dirPath: String, pageIndex: Int): List<Message> {
        val html = readText("$dirPath/messages${pageIndex * PAGE_SIZE}.html") ?: return emptyList()
        return VkArchiveParser.parsePage(html, ownerId)
    }

    /**
     * Read every message of a chat across all its pages, so callers can browse
     * or filter the whole conversation (e.g. by author). Cooperative: yields
     * regularly and honours cancellation. Keeps at most [limit] messages in
     * memory (marking the result truncated past that), but always scans every
     * page to tally per-author message counts so the participant list is complete.
     * [onProgress] is `(processedPages, totalPages)`.
     */
    suspend fun readAllMessages(
        dirPath: String,
        pageCount: Int,
        limit: Int = 50_000,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): LoadedMessages {
        val all = ArrayList<Message>()
        val authorCounts = LinkedHashMap<String, Int>()
        var truncated = false
        for (page in 0 until pageCount) {
            currentCoroutineContext().ensureActive()
            for (m in readPage(dirPath, page)) {
                authorCounts[m.displayAuthor] = (authorCounts[m.displayAuthor] ?: 0) + 1
                if (all.size < limit) all.add(m) else truncated = true
            }
            onProgress(page + 1, pageCount)
            if (page % YIELD_EVERY_PAGES == 0) yield()
        }
        return LoadedMessages(all, truncated, authorCounts)
    }

    /**
     * Search messages whose text contains [query] (case-insensitive). If
     * [peerId] is null, searches all chats. Stops after [limit] hits.
     */
    suspend fun search(
        query: String,
        peerId: String?,
        limit: Int = 500,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onPageProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<SearchHit> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val needle = q.lowercase()
        val hits = ArrayList<SearchHit>()

        val dirs: List<String> = if (peerId != null) listOf(peerId) else titles.keys.toList()

        dirs.forEachIndexed { idx, pid ->
            currentCoroutineContext().ensureActive()
            val dir = "$MESSAGES_DIR/$pid"
            val pages = countPages(dir)
            for (page in 0 until pages) {
                val msgs = readPage(dir, page)
                for (m in msgs) {
                    if (m.text.lowercase().contains(needle)) {
                        hits.add(SearchHit(pid, titles[pid] ?: pid, m))
                        if (hits.size >= limit) return hits
                    }
                }
                // Yield and report progress periodically so huge single chats
                // (tens of thousands of pages) stay responsive and show feedback.
                if (page % YIELD_EVERY_PAGES == 0) {
                    yield()
                    onPageProgress(page + 1, pages)
                }
            }
            onPageProgress(pages, pages)
            onProgress(idx + 1, dirs.size)
        }
        return hits
    }

    /**
     * Number of `messagesN.html` pages in [dir]. Pages are contiguous (0, 50,
     * 100, …), so instead of probing every page we gallop to an upper bound and
     * binary-search the last present one — O(log n) `exists` calls instead of
     * O(n). Matters for chats with thousands of pages and is re-run per search.
     */
    private suspend fun countPages(dir: String): Int {
        val src = source ?: return 0
        suspend fun pageExists(index: Int) = src.exists("$dir/messages${index * PAGE_SIZE}.html")
        if (!pageExists(0)) return 0

        // Gallop: `lo` always exists, `hi` never does.
        var lo = 0
        var hi = 1
        while (pageExists(hi)) {
            lo = hi
            hi *= 2
        }
        // Binary-search the boundary within (lo, hi).
        while (lo + 1 < hi) {
            val mid = (lo + hi) / 2
            if (pageExists(mid)) lo = mid else hi = mid
        }
        return lo + 1
    }

    private suspend fun readText(path: String): String? =
        source?.readBytes(path)?.let { Cp1251.decode(it) }
}
