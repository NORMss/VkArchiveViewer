package ru.normno.vkarchivereader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.normno.vkarchivereader.domain.model.AttachmentType
import ru.normno.vkarchivereader.domain.model.MediaItem

/** Adjustable grid of media thumbnails. [columns] controls images per row. */
@Composable
fun MediaGrid(
    media: List<MediaItem>,
    columns: Int,
    modifier: Modifier = Modifier,
    onItemClick: (Int) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
    ) {
        items(
            count = media.size,
            key = { index -> "$index:${media[index].url}" },
        ) { index ->
            val item = media[index]
            Box(
                Modifier
                    .padding(2.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onItemClick(index) },
            ) {
                if (item.type == AttachmentType.VIDEO) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        Alignment.Center,
                    ) {
                        Icon(
                            AppIcons.PlayArrow,
                            contentDescription = "Видео",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    NetworkImage(
                        url = item.url,
                        contentDescription = item.chatTitle,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
