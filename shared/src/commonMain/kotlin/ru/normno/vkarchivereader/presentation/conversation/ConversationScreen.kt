package ru.normno.vkarchivereader.presentation.conversation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.getKoin
import ru.normno.vkarchivereader.data.repository.ArchiveRepository
import ru.normno.vkarchivereader.domain.model.ChatKind
import ru.normno.vkarchivereader.domain.model.ChatSummary
import ru.normno.vkarchivereader.domain.model.Message
import ru.normno.vkarchivereader.presentation.archive.AuthorHitCount
import ru.normno.vkarchivereader.presentation.components.AppIcons
import ru.normno.vkarchivereader.presentation.components.NetworkImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    chat: ChatSummary,
    onBack: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenImage: (url: String) -> Unit,
    targetMessageId: String? = null,
) {
    val koin = getKoin()
    val viewModel: ConversationViewModel = viewModel(key = chat.peerId) {
        ConversationViewModel(koin.get<ArchiveRepository>(), chat)
    }
    val messagesState by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val browse by viewModel.browse.collectAsStateWithLifecycle()
    val scrollTarget by viewModel.scrollTarget.collectAsStateWithLifecycle()
    val highlightId by viewModel.highlightId.collectAsStateWithLifecycle()

    // Jump to a specific message when opened from a search result.
    LaunchedEffect(targetMessageId) {
        if (targetMessageId != null) viewModel.jumpToMessage(targetMessageId)
    }

    // "Browse by author" only makes sense in groups/conversations (many authors).
    val canBrowseByAuthor = chat.kind != ChatKind.USER

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (browse.active) "Сообщения по автору"
                        else "${chat.messageCount} сообщ. · ${chat.mediaCount} медиа",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                if (canBrowseByAuthor) {
                    IconButton(onClick = { viewModel.toggleAuthorBrowse() }) {
                        Icon(
                            AppIcons.People,
                            contentDescription = "По автору",
                            tint = if (browse.active) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                }
                IconButton(onClick = { viewModel.toggleSearch() }) {
                    Icon(AppIcons.Search, contentDescription = "Поиск")
                }
                if (chat.mediaCount > 0) {
                    IconButton(onClick = onOpenMedia) {
                        Icon(AppIcons.Image, contentDescription = "Медиа чата")
                    }
                }
            },
        )

        if (search.active && !browse.active) {
            OutlinedTextField(
                value = search.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text("Поиск в этом чате") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.runSearch() }),
                trailingIcon = {
                    TextButton(onClick = { viewModel.runSearch() }) { Text("Найти") }
                },
            )
            HorizontalDivider()
        }

        when {
            browse.active ->
                AuthorBrowseContent(browse, viewModel::setBrowseAuthor, onOpenImage)

            search.active && search.executed ->
                InChatSearchResults(
                    search, viewModel::setAuthorFilter, viewModel::jumpFromSearch, onOpenImage,
                )

            else -> MessageList(
                state = messagesState,
                onLoadMore = viewModel::loadNextPage,
                onOpenImage = onOpenImage,
                scrollTarget = scrollTarget,
                highlightId = highlightId,
                onScrolled = viewModel::onScrolledToTarget,
            )
        }
    }
}

@Composable
private fun AuthorBrowseContent(
    state: AuthorBrowseState,
    onSelectAuthor: (String?) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    if (state.loading) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(
                progress = { if (state.total > 0) state.processed.toFloat() / state.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (state.total > 0) "Загрузка сообщений: ${state.processed} / ${state.total}"
                    else "Загрузка сообщений…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    val authorCounts = state.authorCounts
    val visibleMessages = state.visibleMessages

    Column(Modifier.fillMaxSize()) {
        if (authorCounts.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Авторы не найдены") }
            return@Column
        }
        AuthorSelector(
            authors = authorCounts,
            selected = state.authorFilter,
            onSelect = onSelectAuthor,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (state.truncated) {
            Text(
                "Показаны не все сообщения (чат слишком большой)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        HorizontalDivider()
        if (state.authorFilter == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Выберите участника, чтобы увидеть все его сообщения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(count = visibleMessages.size) { i -> MessageBubble(visibleMessages[i], onOpenImage) }
            }
        }
    }
}

@Composable
private fun MessageList(
    state: MessagesState,
    onLoadMore: () -> Unit,
    onOpenImage: (String) -> Unit,
    scrollTarget: String? = null,
    highlightId: String? = null,
    onScrolled: () -> Unit = {},
) {
    if (state.messages.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            if (state.loadingMore) CircularProgressIndicator() else Text("Сообщений нет")
        }
        return
    }

    val listState = rememberLazyListState()

    // Load the next page when the user scrolls near the bottom.
    LaunchedEffect(listState, state.messages.size, state.endReached, state.loadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (!state.endReached && !state.loadingMore && lastVisible >= state.messages.size - 5) {
                    onLoadMore()
                }
            }
    }

    // Scroll to a message requested from search once it has been loaded.
    LaunchedEffect(scrollTarget, state.messages.size) {
        val id = scrollTarget ?: return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            listState.scrollToItem(index)
            onScrolled()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            MessageBubble(message, onOpenImage, highlighted = message.id == highlightId)
        }
        if (state.loadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun InChatSearchResults(
    state: InChatSearchState,
    onSelectAuthor: (String?) -> Unit,
    onOpenMessage: (String) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    if (state.running) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.totalPages > 0) {
                LinearProgressIndicator(
                    progress = { state.scannedPages.toFloat() / state.totalPages },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (state.totalPages > 50)
                        "Поиск… (${state.scannedPages} / ${state.totalPages} стр.)"
                    else "Поиск…",
                )
            }
        }
        return
    }
    if (state.results.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Ничего не найдено") }
        return
    }
    val authorCounts = state.authorCounts
    val visibleResults = state.visibleResults

    Column(Modifier.fillMaxSize()) {
        // Filter results by who wrote them (shown for group/multi-user chats).
        if (authorCounts.size >= 2) {
            AuthorSelector(
                authors = authorCounts,
                selected = state.authorFilter,
                onSelect = onSelectAuthor,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            HorizontalDivider()
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(count = visibleResults.size) { i ->
                val msg = visibleResults[i]
                MessageBubble(msg, onOpenImage, onClick = { onOpenMessage(msg.id) })
            }
        }
    }
}

/**
 * Compact author picker. Shows the current selection as a button; tapping opens a
 * searchable, vertically-scrollable dialog — far easier than a long chip row when a
 * conversation has dozens of participants.
 */
@Composable
private fun AuthorSelector(
    authors: List<AuthorHitCount>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Icon(AppIcons.Person, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            selected ?: "Все участники · ${authors.size}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(AppIcons.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
    }
    if (open) {
        AuthorPickerDialog(
            authors = authors,
            selected = selected,
            onDismiss = { open = false },
            onSelect = { onSelect(it); open = false },
        )
    }
}

@Composable
private fun AuthorPickerDialog(
    authors: List<AuthorHitCount>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(authors, query) {
        if (query.isBlank()) authors
        else authors.filter { it.authorName.contains(query.trim(), ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(12.dp).widthIn(max = 420.dp)) {
                Text(
                    "Участник",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Поиск участника") },
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    item(key = "__all__") {
                        AuthorPickerRow("Все участники", selected == null) { onSelect(null) }
                    }
                    items(filtered, key = { it.authorName }) { author ->
                        AuthorPickerRow(
                            "${author.authorName} · ${author.count}",
                            selected == author.authorName,
                        ) { onSelect(author.authorName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorPickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    onOpenImage: (String) -> Unit,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor =
        if (message.isOutgoing) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant

    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
            border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            Column(Modifier.padding(10.dp)) {
                if (!message.isOutgoing && message.authorName.isNotBlank()) {
                    Text(
                        message.authorName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (message.text.isNotBlank()) {
                    Text(message.text, style = MaterialTheme.typography.bodyMedium)
                }
                message.attachments.forEach { att ->
                    if (att.isImage) {
                        NetworkImage(
                            url = att.url,
                            contentDescription = att.description,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenImage(att.url) },
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(
                                AppIcons.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                att.description,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                message.date?.let {
                    Text(
                        it.raw,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp).heightIn(min = 0.dp),
                    )
                }
            }
        }
    }
}
