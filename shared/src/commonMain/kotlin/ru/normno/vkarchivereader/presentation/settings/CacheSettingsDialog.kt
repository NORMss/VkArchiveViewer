package ru.normno.vkarchivereader.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.normno.vkarchivereader.core.BYTES_IN_GB
import ru.normno.vkarchivereader.core.ImageCacheStore
import ru.normno.vkarchivereader.platform.rememberUrlOpener
import kotlin.math.roundToInt

private const val MIN_GB = 1f
private const val MAX_GB = 20f
private const val DEVELOPER_URL = "https://normno.ru"

/** Dialog to configure the on-device image cache size limit (default 3 GB). */
@Composable
fun CacheSettingsDialog(onDismiss: () -> Unit) {
    val diskSupported = remember { ImageCacheStore.diskCacheDir() != null }
    val urlOpener = rememberUrlOpener()
    var gb by remember {
        mutableStateOf((ImageCacheStore.maxBytes().toFloat() / BYTES_IN_GB).coerceIn(MIN_GB, MAX_GB))
    }
    var cleared by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Кеш изображений") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (diskSupported) {
                    Text(
                        "Изображения сохраняются на устройство, чтобы быстрее открываться в следующий раз.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Ограничение размера: ${gb.roundToInt()} ГБ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(
                        value = gb,
                        onValueChange = { gb = it },
                        valueRange = MIN_GB..MAX_GB,
                        steps = (MAX_GB - MIN_GB).toInt() - 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            ImageCacheStore.clear()
                            cleared = true
                        },
                    ) { Text(if (cleared) "Кеш очищен" else "Очистить кеш") }
                    Text(
                        "Новый лимит вступит в силу после перезапуска приложения.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "В веб-версии кешированием изображений управляет сам браузер, " +
                            "поэтому ограничение размера здесь не применяется.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                HorizontalDivider()
                TextButton(
                    onClick = { urlOpener.open(DEVELOPER_URL) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Разработчик: normno.ru",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ImageCacheStore.setMaxBytes((gb.roundToInt().toLong()) * BYTES_IN_GB)
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
