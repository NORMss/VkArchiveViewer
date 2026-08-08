package ru.normno.vkarchivereader.presentation.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import ru.normno.vkarchivereader.presentation.components.AppIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.normno.vkarchivereader.data.source.ArchivePickOutcome
import ru.normno.vkarchivereader.data.source.DemoArchiveSource
import ru.normno.vkarchivereader.data.source.archiveDropTarget
import ru.normno.vkarchivereader.data.source.rememberArchiveChooser
import ru.normno.vkarchivereader.platform.rememberUrlOpener
import ru.normno.vkarchivereader.presentation.archive.ArchiveUiState

private const val VK_FAQ_URL = "https://vk.ru/faq18145"
private const val DEVELOPER_URL = "https://normno.ru"

@Composable
fun WelcomeScreen(
    state: ArchiveUiState,
    onResult: (ArchivePickOutcome) -> Unit,
    modifier: Modifier = Modifier,
) {
    val launchChooser = rememberArchiveChooser(onResult)
    val loading = state is ArchiveUiState.Loading
    val urlOpener = rememberUrlOpener()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .archiveDropTarget(enabled = !loading, onResult = onResult)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = { urlOpener.open(VK_FAQ_URL) },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                AppIcons.Info,
                contentDescription = "Как скачать архив ВКонтакте",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Text(
                "VK Archive Reader",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Удобный просмотр сообщений и медиа из архива выгрузки VK.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(16.dp),
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        AppIcons.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Перетащите сюда папку архива или zip-файл",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Папка должна содержать index.html и каталог messages/",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = launchChooser, enabled = !loading) {
                        Text("Выбрать архив")
                    }
                }
            }

            TextButton(
                onClick = { onResult(ArchivePickOutcome.Success(DemoArchiveSource())) },
                enabled = !loading,
            ) {
                Text("Нет своего архива? Открыть демо-данные")
            }

            when (state) {
                is ArchiveUiState.Loading -> {
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = { state.processed.toFloat() / state.total },
                            modifier = Modifier.width(280.dp),
                        )
                        Text(
                            "Обработка чатов: ${state.processed} / ${state.total}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    } else {
                        CircularProgressIndicator()
                        Text("Чтение архива…", style = MaterialTheme.typography.labelMedium)
                    }
                }

                is ArchiveUiState.Error -> Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> Unit
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { urlOpener.open(DEVELOPER_URL) }) {
                Text(
                    "Разработчик: normno.ru",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
