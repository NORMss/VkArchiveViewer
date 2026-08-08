package ru.normno.vkarchivereader.presentation.face

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.normno.vkarchivereader.domain.model.AttachmentType
import ru.normno.vkarchivereader.domain.model.MediaItem
import ru.normno.vkarchivereader.download.DownloadResult
import ru.normno.vkarchivereader.download.fileNameFor
import ru.normno.vkarchivereader.download.rememberMediaDownloader
import ru.normno.vkarchivereader.face.FaceGroup
import ru.normno.vkarchivereader.face.GroupPhoto
import ru.normno.vkarchivereader.presentation.components.AppIcons
import ru.normno.vkarchivereader.presentation.components.FullscreenMediaViewer
import ru.normno.vkarchivereader.presentation.components.NetworkImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceGroupsScreen(
    images: List<MediaItem>,
    archiveId: String,
    onBack: () -> Unit,
) {
    val viewModel: FaceViewModel = viewModel(key = "faces-$archiveId") { FaceViewModel(archiveId) }
    val downloader = rememberMediaDownloader()
    val scope = rememberCoroutineScope()

    var selectedGroup by remember { mutableStateOf<FaceGroup?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(selectedGroup?.name ?: "Лица") },
            navigationIcon = {
                IconButton(onClick = { if (selectedGroup != null) selectedGroup = null else onBack() }) {
                    Icon(AppIcons.ArrowBack, contentDescription = "Назад")
                }
            },
        )

        if (!viewModel.supported) {
            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(
                    "Группировка фото по лицам доступна в десктоп-версии (обработка идёт локально на вашем устройстве).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        when (val group = selectedGroup) {
            null -> GroupsList(
                viewModel = viewModel,
                images = images.filter { it.type != AttachmentType.VIDEO },
                onOpenGroup = { selectedGroup = it },
            )

            else -> GroupDetail(
                group = group,
                viewModel = viewModel,
                onDownloadAll = { photos ->
                    scope.launch {
                        var ok = 0
                        photos.forEachIndexed { i, p ->
                            if (downloader.download(p.url, fileNameFor(p.url, i, "group${group.id}")) == DownloadResult.SAVED) ok++
                        }
                        status = "Скачано $ok из ${photos.size}"
                    }
                },
                onDownloadOne = { item ->
                    scope.launch {
                        val r = downloader.download(item.url, fileNameFor(item.url, 0, "photo"))
                        status = if (r == DownloadResult.SAVED) "Фото сохранено" else "Не удалось скачать"
                    }
                },
            )
        }

        status?.let {
            HorizontalDivider()
            Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GroupsList(
    viewModel: FaceViewModel,
    images: List<MediaItem>,
    onOpenGroup: (FaceGroup) -> Unit,
) {
    val groups by viewModel.groups.collectAsState(emptyList())
    val processing by viewModel.processing.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (val p = processing) {
                is FaceProcessingState.Running -> {
                    val total = p.progress.totalImages.coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { p.progress.processedImages.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Обработано ${p.progress.processedImages}/${p.progress.totalImages} · лиц ${p.progress.faces} · групп ${p.progress.groups}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    OutlinedButton(onClick = { viewModel.cancel() }) { Text("Остановить") }
                }

                is FaceProcessingState.Failed -> Text(
                    p.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.process(images) }) {
                            Icon(AppIcons.People, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Найти лица (${images.size} фото)")
                        }
                        if (groups.isNotEmpty()) {
                            OutlinedButton(onClick = { viewModel.clear() }) { Text("Очистить") }
                        }
                    }
                }
            }
            Text(
                "Обработка идёт локально; фото не сохраняются на устройство — в базе только ссылки, группа и чат.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Группы появятся после обработки", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            ) {
                items(groups, key = { it.id }) { group ->
                    Column(
                        Modifier.padding(6.dp).clickable { onOpenGroup(group) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
                            group.coverUrl?.let {
                                NetworkImage(url = it, contentDescription = group.name, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Text(
                            group.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${group.photoCount} фото",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupDetail(
    group: FaceGroup,
    viewModel: FaceViewModel,
    onDownloadAll: (List<GroupPhoto>) -> Unit,
    onDownloadOne: (MediaItem) -> Unit,
) {
    var photos by remember(group.id) { mutableStateOf<List<GroupPhoto>>(emptyList()) }
    var name by remember(group.id) { mutableStateOf(group.name) }
    var viewerIndex by remember(group.id) { mutableStateOf<Int?>(null) }

    androidx.compose.runtime.LaunchedEffect(group.id) {
        photos = viewModel.photosOf(group.id)
    }

    val mediaItems = remember(photos) {
        photos.map { MediaItem(it.url, AttachmentType.PHOTO, it.chatPeerId, it.chatTitle, null) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Имя группы (например, Имя Фамилия)") },
            )
            TextButton(onClick = { viewModel.rename(group.id, name.trim().ifBlank { group.name }) }) {
                Text("Сохранить")
            }
        }
        Row(Modifier.padding(horizontal = 12.dp)) {
            OutlinedButton(onClick = { onDownloadAll(photos) }) {
                Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Скачать все (${photos.size})")
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
        ) {
            items(mediaItems.size, key = { mediaItems[it].url + it }) { index ->
                Box(
                    Modifier.padding(2.dp).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                        .clickable { viewerIndex = index },
                ) {
                    NetworkImage(
                        url = mediaItems[index].url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    IconButton(
                        onClick = { onDownloadOne(mediaItems[index]) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            AppIcons.Download,
                            contentDescription = "Скачать",
                            tint = MaterialTheme.colorScheme.surface,
                        )
                    }
                }
            }
        }
    }

    viewerIndex?.let { idx ->
        FullscreenMediaViewer(
            items = mediaItems,
            startIndex = idx,
            onClose = { viewerIndex = null },
            onDownload = onDownloadOne,
        )
    }
}
