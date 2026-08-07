package ru.normno.vkarchivereader.presentation.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.normno.vkarchivereader.core.ioDispatcher
import ru.normno.vkarchivereader.data.repository.ArchiveData
import ru.normno.vkarchivereader.data.repository.ArchiveRepository
import ru.normno.vkarchivereader.data.repository.SearchHit
import ru.normno.vkarchivereader.data.source.ArchivePickOutcome
import ru.normno.vkarchivereader.domain.model.ChatSortOrder
import ru.normno.vkarchivereader.domain.model.ChatSummary

sealed interface ArchiveUiState {
    data object Empty : ArchiveUiState
    data class Loading(val processed: Int, val total: Int) : ArchiveUiState
    data object Loaded : ArchiveUiState
    data class Error(val message: String) : ArchiveUiState
}

data class IndexingState(
    val running: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
)

data class GlobalSearchState(
    val query: String = "",
    val running: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val executed: Boolean = false,
    /** When set, only messages of this person/group (peer) are shown. */
    val peerFilter: String? = null,
    /** When set, only messages written by this author are shown. */
    val authorFilter: String? = null,
) {
    /** Hits after applying [peerFilter] only (before the author filter). */
    private val peerHits: List<SearchHit>
        get() = if (peerFilter == null) hits else hits.filter { it.peerId == peerFilter }

    /** Hits after applying both [peerFilter] and [authorFilter]. */
    val visibleHits: List<SearchHit>
        get() = if (authorFilter == null) peerHits
        else peerHits.filter { it.message.displayAuthor == authorFilter }

    /**
     * Distinct peers present in [hits] with their hit counts, ordered by count
     * descending — used to offer per-person/group filters over the results.
     */
    val peerCounts: List<PeerHitCount>
        get() = hits
            .groupingBy { it.peerId to it.chatTitle }
            .eachCount()
            .map { (key, count) -> PeerHitCount(key.first, key.second, count) }
            .sortedByDescending { it.count }

    /**
     * Distinct authors among the currently peer-filtered hits, ordered by count
     * descending — used to filter results by who wrote them inside a group.
     */
    val authorCounts: List<AuthorHitCount>
        get() = peerHits
            .groupingBy { it.message.displayAuthor }
            .eachCount()
            .map { (name, count) -> AuthorHitCount(name, count) }
            .sortedByDescending { it.count }
}

/** How many search hits belong to a single person/group. */
data class PeerHitCount(
    val peerId: String,
    val chatTitle: String,
    val count: Int,
)

/** How many search hits were written by a single author. */
data class AuthorHitCount(
    val authorName: String,
    val count: Int,
)

class ArchiveViewModel(
    private val repository: ArchiveRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArchiveUiState>(ArchiveUiState.Empty)
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    /** Live archive data; updates as background media indexing fills it in. */
    val data: StateFlow<ArchiveData?> = repository.data

    private val _indexing = MutableStateFlow(IndexingState())
    val indexing: StateFlow<IndexingState> = _indexing.asStateFlow()

    private val _sortOrder = MutableStateFlow(ChatSortOrder.LAST_MESSAGE)
    val sortOrder: StateFlow<ChatSortOrder> = _sortOrder.asStateFlow()

    private val _chatFilter = MutableStateFlow("")
    val chatFilter: StateFlow<String> = _chatFilter.asStateFlow()

    private val _search = MutableStateFlow(GlobalSearchState())
    val search: StateFlow<GlobalSearchState> = _search.asStateFlow()

    private var indexJob: Job? = null

    fun onArchivePicked(outcome: ArchivePickOutcome) {
        when (outcome) {
            is ArchivePickOutcome.Cancelled -> Unit
            is ArchivePickOutcome.Failure ->
                _uiState.value = ArchiveUiState.Error(outcome.message)
            is ArchivePickOutcome.Success -> openArchive(outcome)
        }
    }

    private fun openArchive(outcome: ArchivePickOutcome.Success) {
        _uiState.value = ArchiveUiState.Loading(0, 0)
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    repository.open(outcome.source) { done, total ->
                        _uiState.value = ArchiveUiState.Loading(done, total)
                    }
                }
                _uiState.value = ArchiveUiState.Loaded
                startMediaIndexing()
            } catch (e: Throwable) {
                _uiState.value = ArchiveUiState.Error(e.message ?: "Не удалось открыть архив")
            }
        }
    }

    private fun startMediaIndexing() {
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            _indexing.value = IndexingState(running = true)
            try {
                withContext(ioDispatcher) {
                    repository.indexMedia { done, total ->
                        _indexing.value = IndexingState(running = true, processed = done, total = total)
                    }
                }
            } finally {
                _indexing.value = _indexing.value.copy(running = false)
            }
        }
    }

    fun setSortOrder(order: ChatSortOrder) { _sortOrder.value = order }

    fun setChatFilter(text: String) { _chatFilter.value = text }

    /** Filter (by title) + sort the loaded chats for display. */
    fun visibleChats(data: ArchiveData, filter: String, order: ChatSortOrder): List<ChatSummary> {
        val filtered = if (filter.isBlank()) {
            data.chats
        } else {
            val q = filter.trim().lowercase()
            data.chats.filter { it.title.lowercase().contains(q) }
        }
        return order.sort(filtered)
    }

    fun onSearchQueryChange(query: String) {
        _search.value = _search.value.copy(query = query)
    }

    fun runGlobalSearch(peerId: String? = null) {
        val query = _search.value.query
        if (query.isBlank()) {
            _search.value = _search.value.copy(
                hits = emptyList(), executed = false, peerFilter = null, authorFilter = null,
            )
            return
        }
        _search.value = _search.value.copy(
            running = true, executed = true, peerFilter = null, authorFilter = null,
        )
        viewModelScope.launch {
            val hits = withContext(ioDispatcher) {
                repository.search(query, peerId)
            }
            _search.value = _search.value.copy(running = false, hits = hits)
        }
    }

    /** Narrow the shown results to a single person/group, or null for all. */
    fun setSearchPeerFilter(peerId: String?) {
        // Authors differ per peer, so reset the author filter when the peer changes.
        _search.value = _search.value.copy(peerFilter = peerId, authorFilter = null)
    }

    /** Narrow the shown results to a single author, or null for everyone. */
    fun setSearchAuthorFilter(author: String?) {
        _search.value = _search.value.copy(authorFilter = author)
    }

    fun clearSearch() { _search.value = GlobalSearchState() }

    /** Reset both the message search and the chat title filter (the shared text field). */
    fun resetSearch() {
        _chatFilter.value = ""
        _search.value = GlobalSearchState()
    }

    fun reset() {
        indexJob?.cancel()
        repository.close()
        _uiState.value = ArchiveUiState.Empty
        _indexing.value = IndexingState()
        _chatFilter.value = ""
        _sortOrder.value = ChatSortOrder.LAST_MESSAGE
        _search.value = GlobalSearchState()
    }
}
