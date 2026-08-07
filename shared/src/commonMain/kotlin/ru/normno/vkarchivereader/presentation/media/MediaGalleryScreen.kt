package ru.normno.vkarchivereader.presentation.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.normno.vkarchivereader.data.repository.ArchiveData
import ru.normno.vkarchivereader.domain.model.AttachmentType
import ru.normno.vkarchivereader.domain.model.ChatSummary
import ru.normno.vkarchivereader.domain.model.MediaItem
import ru.normno.vkarchivereader.presentation.components.AppIcons
import ru.normno.vkarchivereader.presentation.components.FullscreenMediaViewer
import ru.normno.vkarchivereader.presentation.components.MediaGrid

private const val MIN_COLUMNS = 1
private const val MAX_COLUMNS = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    data: ArchiveData,
    peerId: String?,
    chatTitle: String?,
    onBack: () -> Unit,
    onOpenFaces: (List<MediaItem>) -> Unit = {},
) {
    var columns by rememberSaveable { mutableStateOf(4) }
    var onlyImages by rememberSaveable { mutableStateOf(true) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var showChatFilter by remember { mutableStateOf(false) }

    // Global gallery only: which chats' media to include (default — all).
    val chatsWithMedia = remember(data) {
        data.chats.filter { it.mediaCount > 0 }.sortedByDescending { it.mediaCount }
    }
    var selectedChats by remember(data) {
        mutableStateOf(chatsWithMedia.map { it.peerId }.toSet())
    }
    val isGlobal = peerId == null

    val media: List<MediaItem> = remember(data, peerId, onlyImages, selectedChats) {
        data.media
            .asSequence()
            .filter { if (isGlobal) it.chatPeerId in selectedChats else it.chatPeerId == peerId }
            .filter { !onlyImages || it.type != AttachmentType.VIDEO }
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        if (chatTitle != null) "Медиа · $chatTitle" else "Все медиа",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${media.size} файлов", style = MaterialTheme.typography.labelSmall)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = "Назад")
                }
            },
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("В ряду: $columns", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = columns.toFloat(),
                onValueChange = { columns = it.toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS) },
                valueRange = MIN_COLUMNS.toFloat()..MAX_COLUMNS.toFloat(),
                steps = MAX_COLUMNS - MIN_COLUMNS - 1,
                modifier = Modifier.width(200.dp),
            )
            FilterChip(
                selected = onlyImages,
                onClick = { onlyImages = !onlyImages },
                label = { Text("Только фото") },
            )
        }

        if (isGlobal) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { showChatFilter = true }) {
                    Icon(AppIcons.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        "  Чаты: ${selectedChats.size} из ${chatsWithMedia.size}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = { onOpenFaces(media) }) {
                    Icon(AppIcons.People, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Лица")
                }
            }
        }

        HorizontalDivider()

        if (media.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (isGlobal && selectedChats.isEmpty()) "Выберите хотя бы один чат"
                    else "Медиа не найдено",
                )
            }
        } else {
            MediaGrid(
                media = media,
                columns = columns,
                modifier = Modifier.fillMaxSize(),
                onItemClick = { index -> viewerIndex = index },
            )
        }
    }

    if (showChatFilter) {
        ChatMediaFilterDialog(
            chats = chatsWithMedia,
            selected = selectedChats,
            onToggle = { id ->
                selectedChats = if (id in selectedChats) selectedChats - id else selectedChats + id
            },
            onSelectAll = { selectedChats = chatsWithMedia.map { it.peerId }.toSet() },
            onClearAll = { selectedChats = emptySet() },
            onDismiss = { showChatFilter = false },
        )
    }

    viewerIndex?.let { idx ->
        FullscreenMediaViewer(
            items = media,
            startIndex = idx,
            onClose = { viewerIndex = null },
        )
    }
}

@Composable
private fun ChatMediaFilterDialog(
    chats: List<ChatSummary>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(chats, query) {
        if (query.isBlank()) chats
        else chats.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Чаты для галереи") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Поиск чата") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSelectAll) { Text("Выбрать все") }
                    TextButton(onClick = onClearAll) { Text("Снять все") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(visible, key = { it.peerId }) { chat ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(chat.peerId) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = chat.peerId in selected,
                                onCheckedChange = { onToggle(chat.peerId) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    chat.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${chat.mediaCount} медиа",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}
