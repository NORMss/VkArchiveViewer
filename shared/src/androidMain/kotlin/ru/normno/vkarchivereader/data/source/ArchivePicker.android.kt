package ru.normno.vkarchivereader.data.source

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

@Composable
actual fun rememberArchiveChooser(onResult: (ArchivePickOutcome) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            callback.value(ArchivePickOutcome.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { readZipUri(context, uri) }.fold(
                    onSuccess = { ArchivePickOutcome.Success(it) },
                    onFailure = { ArchivePickOutcome.Failure(it.message ?: "Ошибка чтения архива") },
                )
            }
            callback.value(outcome)
        }
    }

    return {
        launcher.launch(
            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
        )
    }
}

actual fun Modifier.archiveDropTarget(
    enabled: Boolean,
    onResult: (ArchivePickOutcome) -> Unit,
): Modifier = this // No external file drop on Android.

private fun readZipUri(context: Context, uri: android.net.Uri): ArchiveSource {
    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "archive.zip"
    val entries = HashMap<String, ByteArray>()
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name.replace('\\', '/')] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
    } ?: error("Не удалось открыть файл")

    val marker = "messages/index-messages.html"
    val prefix = entries.keys.firstOrNull { it.endsWith(marker) }?.removeSuffix(marker)
        ?: entries.keys.firstOrNull { it.endsWith("index.html") }?.removeSuffix("index.html")
        ?: ""
    val rebased = entries.filterKeys { it.startsWith(prefix) }
        .mapKeys { it.key.removePrefix(prefix) }
    return MapArchiveSource(name, rebased)
}

private class MapArchiveSource(
    override val displayName: String,
    private val entries: Map<String, ByteArray>,
) : ArchiveSource {
    override suspend fun exists(path: String): Boolean = entries.containsKey(path)
    override suspend fun readBytes(path: String): ByteArray? = entries[path]
    override suspend fun listDirs(path: String): List<String> {
        val prefix = if (path.isEmpty()) "" else "$path/"
        return entries.keys.asSequence()
            .filter { it.startsWith(prefix) }
            .mapNotNull {
                val rest = it.removePrefix(prefix)
                val seg = rest.substringBefore('/')
                if (seg.isNotEmpty() && rest.contains('/')) seg else null
            }
            .distinct().toList()
    }
}
