package ru.normno.vkarchivereader.presentation.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.normno.vkarchivereader.core.ioDispatcher
import ru.normno.vkarchivereader.data.repository.ArchiveRepository
import ru.normno.vkarchivereader.domain.model.ChatSummary
import ru.normno.vkarchivereader.domain.model.Message
import ru.normno.vkarchivereader.presentation.archive.AuthorHitCount

data class MessagesState(
    val messages: List<Message> = emptyList(),
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
)

data class InChatSearchState(
    val query: String = "",
    val active: Boolean = false,
    val running: Boolean = false,
    val results: List<Message> = emptyList(),
    val executed: Boolean = false,
    /** When set, only messages written by this author are shown. */
    val authorFilter: String? = null,
    /** Scan progress for big chats: pages processed / total. */
    val scannedPages: Int = 0,
    val totalPages: Int = 0,
) {
    /** Search results after applying [authorFilter]. */
    val visibleResults: List<Message>
        get() = if (authorFilter == null) results
        else results.filter { it.displayAuthor == authorFilter }

    /**
     * Distinct authors among the results, ordered by count descending — used to
     * offer per-author filter chips inside group/multi-user conversations.
     */
    val authorCounts: List<AuthorHitCount>
        get() = results
            .groupingBy { it.displayAuthor }
            .eachCount()
            .map { (name, count) -> AuthorHitCount(name, count) }
            .sortedByDescending { it.count }
}

/**
 * "Browse by author" mode: all messages of the chat are loaded once, then can be
 * filtered down to a single participant — for reading everything a person wrote
 * in a group without a search query.
 */
data class AuthorBrowseState(
    val active: Boolean = false,
    val loading: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val messages: List<Message> = emptyList(),
    /** Full per-author message tally across the whole chat (not just loaded pages). */
    val authorTotals: Map<String, Int> = emptyMap(),
    val authorFilter: String? = null,
    val truncated: Boolean = false,
) {
    /** Participants of the chat with how many messages each wrote, by count desc. */
    val authorCounts: List<AuthorHitCount>
        get() = authorTotals
            .map { (name, count) -> AuthorHitCount(name, count) }
            .sortedByDescending { it.count }

    /** Messages after applying [authorFilter] (all messages when none is selected). */
    val visibleMessages: List<Message>
        get() = if (authorFilter == null) messages
        else messages.filter { it.displayAuthor == authorFilter }
}

/**
 * Per-chat screen. Messages are loaded incrementally one HTML page (50 messages)
 * at a time — a lightweight, fully-multiplatform pager (the AndroidX/Cash paging
 * libraries do not support wasmJs, which we need for the web target).
 */
class ConversationViewModel(
    private val repository: ArchiveRepository,
    private val chat: ChatSummary,
) : ViewModel() {

    val title: String get() = chat.title
    val chatSummary: ChatSummary get() = chat

    private var nextPage = 0

    private val _state = MutableStateFlow(MessagesState())
    val state: StateFlow<MessagesState> = _state.asStateFlow()

    private val _search = MutableStateFlow(InChatSearchState())
    val search: StateFlow<InChatSearchState> = _search.asStateFlow()

    private val _browse = MutableStateFlow(AuthorBrowseState())
    val browse: StateFlow<AuthorBrowseState> = _browse.asStateFlow()
    private var browseJob: Job? = null

    /** Id of a message to scroll to (from a search result); consumed by the UI. */
    private val _scrollTarget = MutableStateFlow<String?>(null)
    val scrollTarget: StateFlow<String?> = _scrollTarget.asStateFlow()

    /** Id of a message to briefly highlight after jumping to it. */
    private val _highlightId = MutableStateFlow<String?>(null)
    val highlightId: StateFlow<String?> = _highlightId.asStateFlow()
    private var jumpJob: Job? = null

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        if (_state.value.loadingMore || _state.value.endReached) return
        viewModelScope.launch { loadPageInline() }
    }

    /** Load the next page and suspend until it is appended. Safe to call in a loop. */
    private suspend fun loadPageInline() {
        val current = _state.value
        if (current.loadingMore || current.endReached) return
        _state.value = current.copy(loadingMore = true)
        val page = withContext(ioDispatcher) {
            repository.readPage(chat.dirPath, nextPage)
        }
        nextPage++
        _state.value = MessagesState(
            messages = _state.value.messages + page,
            loadingMore = false,
            endReached = nextPage >= chat.pageCount,
        )
    }

    /**
     * Load pages until the message [id] is in memory, then ask the UI to scroll
     * to it and highlight it. Pages load newest-first, so reaching an old message
     * may take a while; capped to keep memory bounded on huge chats.
     */
    fun jumpToMessage(id: String) {
        jumpJob?.cancel()
        jumpJob = viewModelScope.launch {
            var loaded = 0
            while (_state.value.messages.none { it.id == id } &&
                !_state.value.endReached && loaded < JUMP_MAX_PAGES
            ) {
                loadPageInline()
                loaded++
            }
            if (_state.value.messages.any { it.id == id }) {
                _scrollTarget.value = id
                _highlightId.value = id
                launch {
                    delay(2500)
                    if (_highlightId.value == id) _highlightId.value = null
                }
            }
        }
    }

    /** The UI has scrolled to the pending target; clear it. */
    fun onScrolledToTarget() { _scrollTarget.value = null }

    /** Close the in-chat search and jump to the tapped result in the message list. */
    fun jumpFromSearch(id: String) {
        _search.value = InChatSearchState()
        _browse.value = AuthorBrowseState()
        jumpToMessage(id)
    }

    companion object {
        // ~100k messages: enough for any reasonable jump without risking OOM.
        private const val JUMP_MAX_PAGES = 2000
    }

    fun toggleSearch() {
        _search.value = if (_search.value.active) InChatSearchState() else InChatSearchState(active = true)
    }

    fun onQueryChange(query: String) {
        // Clearing the field resets the results so stale hits don't linger.
        _search.value = if (query.isBlank()) {
            _search.value.copy(query = "", results = emptyList(), executed = false, authorFilter = null)
        } else {
            _search.value.copy(query = query)
        }
    }

    fun runSearch() {
        val query = _search.value.query
        if (query.isBlank()) {
            _search.value = _search.value.copy(results = emptyList(), executed = false, authorFilter = null)
            return
        }
        _search.value = _search.value.copy(
            running = true, executed = true, authorFilter = null,
            scannedPages = 0, totalPages = chat.pageCount,
        )
        viewModelScope.launch {
            val results = withContext(ioDispatcher) {
                repository.search(
                    query, chat.peerId,
                    onPageProgress = { done, total ->
                        _search.value = _search.value.copy(scannedPages = done, totalPages = total)
                    },
                ).map { it.message }
            }
            _search.value = _search.value.copy(running = false, results = results)
        }
    }

    /** Narrow the shown results to a single author, or null for everyone. */
    fun setAuthorFilter(author: String?) {
        _search.value = _search.value.copy(authorFilter = author)
    }

    /** Enter/leave "browse by author" mode; loads all chat messages on entry. */
    fun toggleAuthorBrowse() {
        if (_browse.value.active) {
            browseJob?.cancel()
            _browse.value = AuthorBrowseState()
            return
        }
        _browse.value = AuthorBrowseState(active = true, loading = true, total = chat.pageCount)
        browseJob = viewModelScope.launch {
            val loaded = withContext(ioDispatcher) {
                repository.readAllMessages(chat.dirPath, chat.pageCount) { done, total ->
                    _browse.value = _browse.value.copy(processed = done, total = total)
                }
            }
            _browse.value = _browse.value.copy(
                loading = false,
                messages = loaded.messages,
                authorTotals = loaded.authorCounts,
                truncated = loaded.truncated,
            )
        }
    }

    /** Pick a participant to show only their messages, or null for the whole chat. */
    fun setBrowseAuthor(author: String?) {
        _browse.value = _browse.value.copy(authorFilter = author)
    }
}
