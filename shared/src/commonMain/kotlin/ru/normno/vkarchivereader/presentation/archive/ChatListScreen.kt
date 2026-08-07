package ru.normno.vkarchivereader.presentation.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.normno.vkarchivereader.data.repository.ArchiveData
import ru.normno.vkarchivereader.domain.model.ChatKind
import ru.normno.vkarchivereader.domain.model.ChatSortOrder
import ru.normno.vkarchivereader.domain.model.ChatSummary
import ru.normno.vkarchivereader.presentation.components.AppIcons
import ru.normno.vkarchivereader.presentation.settings.CacheSettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    data: ArchiveData,
    viewModel: ArchiveViewModel,
    onOpenChat: (ChatSummary, String?) -> Unit,
    onOpenMedia: () -> Unit,
    onClose: () -> Unit,
) {
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val filter by viewModel.chatFilter.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val indexing by viewModel.indexing.collectAsStateWithLifecycle()

    val chats = remember(data, filter, sortOrder) {
        viewModel.visibleChats(data, filter, sortOrder)
    }
    var showSettings by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(data.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${data.chats.size} чатов · ${data.media.size} медиа",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            actions = {
                IconButton(onClick = { showSettings = true }) {
                    Icon(AppIcons.Settings, contentDescription = "Настройки")
                }
                TextButton(onClick = onOpenMedia) {
                    Icon(AppIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Медиа")
                }
                TextButton(onClick = onClose) { Text("Закрыть") }
            },
        )

        if (showSettings) {
            CacheSettingsDialog(onDismiss = { showSettings = false })
        }

        // Search + sort controls
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            OutlinedTextField(
                value = filter,
                onValueChange = {
                    viewModel.setChatFilter(it)
                    viewModel.onSearchQueryChange(it)
                    if (it.isBlank()) viewModel.clearSearch()
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Фильтр чатов / поиск по сообщениям") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.runGlobalSearch(null) }),
                trailingIcon = {
                    if (filter.isNotBlank()) {
                        TextButton(onClick = { viewModel.runGlobalSearch(null) }) { Text("Найти") }
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortMenu(current = sortOrder, onSelected = viewModel::setSortOrder)
                if (search.executed) {
                    TextButton(onClick = { viewModel.resetSearch() }) { Text("Сбросить поиск ✕") }
                }
            }
        }
        if (indexing.running) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (indexing.total > 0)
                        "Индексация медиа: ${indexing.processed} / ${indexing.total}"
                    else "Индексация медиа…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = {
                    if (indexing.total > 0) indexing.processed.toFloat() / indexing.total else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalDivider()

        when {
            search.executed -> SearchResults(
                state = search,
                data = data,
                onOpenChat = onOpenChat,
                onSelectPeer = viewModel::setSearchPeerFilter,
                onSelectAuthor = viewModel::setSearchAuthorFilter,
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(chats, key = { it.peerId }) { chat ->
                    ChatRow(chat = chat, onClick = { onOpenChat(chat, null) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: GlobalSearchState,
    data: ArchiveData,
    onOpenChat: (ChatSummary, String?) -> Unit,
    onSelectPeer: (String?) -> Unit,
    onSelectAuthor: (String?) -> Unit,
) {
    if (state.running) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Поиск «${state.query}»…")
        }
        return
    }
    if (state.hits.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Ничего не найдено по запросу «${state.query}»")
        }
        return
    }
    val byPeer = remember(data) { data.chats.associateBy { it.peerId } }
    val peerCounts = remember(state.hits) { state.peerCounts }
    val authorCounts = remember(state.hits, state.peerFilter) { state.authorCounts }
    val visibleHits = state.visibleHits

    Column(Modifier.fillMaxSize()) {
        // Per-person/group filter chips: pick one to show only their messages.
        PeerFilterChips(
            peers = peerCounts,
            totalHits = state.hits.size,
            selected = state.peerFilter,
            onSelect = onSelectPeer,
        )
        // Second level: once a peer is chosen, filter by who wrote the message
        // (useful inside groups/conversations with several participants).
        if (state.peerFilter != null && authorCounts.size >= 2) {
            AuthorFilterChips(
                authors = authorCounts,
                selected = state.authorFilter,
                onSelect = onSelectAuthor,
            )
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(visibleHits.size, key = { "${visibleHits[it].peerId}-${visibleHits[it].message.id}" }) { i ->
                val hit = visibleHits[i]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { byPeer[hit.peerId]?.let { onOpenChat(it, hit.message.id) } }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        hit.chatTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // Author of the message (relevant inside groups/conversations).
                    if (hit.message.displayAuthor != hit.chatTitle) {
                        Text(
                            hit.message.displayAuthor,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        hit.message.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    hit.message.date?.let {
                        Text(it.raw, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PeerFilterChips(
    peers: List<PeerHitCount>,
    totalHits: Int,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "__all__") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Все · $totalHits") },
            )
        }
        items(peers, key = { it.peerId }) { peer ->
            FilterChip(
                selected = selected == peer.peerId,
                onClick = { onSelect(if (selected == peer.peerId) null else peer.peerId) },
                leadingIcon = {
                    Icon(
                        peerKindIcon(ChatSummary.kindOf(peer.peerId)),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = {
                    Text(
                        "${peer.chatTitle} · ${peer.count}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun AuthorFilterChips(
    authors: List<AuthorHitCount>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "__all_authors__") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                leadingIcon = {
                    Icon(AppIcons.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("Все авторы") },
            )
        }
        items(authors, key = { it.authorName }) { author ->
            FilterChip(
                selected = selected == author.authorName,
                onClick = { onSelect(if (selected == author.authorName) null else author.authorName) },
                label = {
                    Text(
                        "${author.authorName} · ${author.count}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

private fun peerKindIcon(kind: ChatKind) = when (kind) {
    ChatKind.USER -> AppIcons.Person
    ChatKind.GROUP -> AppIcons.People
    ChatKind.CONVERSATION -> AppIcons.People
}

@Composable
private fun SortMenu(current: ChatSortOrder, onSelected: (ChatSortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Сортировка: ${current.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChatSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    onClick = { onSelected(order); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(avatarColor(chat.kind)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                chat.title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                chat.lastMessageDate?.let {
                    Text(
                        "${it.day}.${it.month}.${it.year}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                chat.lastMessagePreview.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.Message,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${chat.messageCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    AppIcons.Image,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${chat.mediaCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun avatarColor(kind: ChatKind) = when (kind) {
    ChatKind.USER -> MaterialTheme.colorScheme.primary
    ChatKind.GROUP -> MaterialTheme.colorScheme.tertiary
    ChatKind.CONVERSATION -> MaterialTheme.colorScheme.secondary
}
