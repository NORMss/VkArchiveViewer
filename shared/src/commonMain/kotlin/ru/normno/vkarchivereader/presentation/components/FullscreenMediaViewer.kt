package ru.normno.vkarchivereader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.focusable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.normno.vkarchivereader.domain.model.MediaItem

/** Full-screen, swipeable viewer overlay for the media list. */
@Composable
fun FullscreenMediaViewer(
    items: List<MediaItem>,
    startIndex: Int,
    onClose: () -> Unit,
    onDownload: ((MediaItem) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Grab focus so hardware-keyboard shortcuts (Esc / ← / →) work immediately.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> { onClose(); true }
                    Key.DirectionLeft -> {
                        val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                        scope.launch { pagerState.animateScrollToPage(target) }
                        true
                    }
                    Key.DirectionRight -> {
                        val target = (pagerState.currentPage + 1).coerceAtMost(items.lastIndex)
                        scope.launch { pagerState.animateScrollToPage(target) }
                        true
                    }
                    else -> false
                }
            },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize().padding(top = 56.dp, bottom = 64.dp), Alignment.Center) {
                NetworkImage(
                    url = item.url,
                    contentDescription = item.chatTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // Top bar
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${pagerState.currentPage + 1} / ${items.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDownload != null) {
                    TextButton(onClick = { onDownload(items[pagerState.currentPage]) }) {
                        Icon(AppIcons.Download, contentDescription = "Скачать", tint = Color.White)
                        Text("Скачать", color = Color.White)
                    }
                }
                TextButton(onClick = onClose) {
                    Icon(AppIcons.Close, contentDescription = "Закрыть", tint = Color.White)
                    Text("Закрыть", color = Color.White)
                }
            }
        }

        // Bottom caption
        val current = items[pagerState.currentPage]
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                current.chatTitle,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            current.messageDate?.let {
                Text(
                    it.raw,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
