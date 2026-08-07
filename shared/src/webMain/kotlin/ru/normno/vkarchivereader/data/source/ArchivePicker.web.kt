package ru.normno.vkarchivereader.data.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Extra shape over [File] to read the non-standard directory-relative path. */
private external interface DirFile {
    val webkitRelativePath: String
}

/**
 * Web archive picking. Uses a hidden `<input type="file" webkitdirectory>` so the
 * user can select the **already-unpacked archive folder** directly in the browser
 * (no zip needed). File contents are read lazily on demand via [FileReader].
 *
 * Shared by the js and wasmJs targets through the `webMain` source set.
 */
@Composable
actual fun rememberArchiveChooser(onResult: (ArchivePickOutcome) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.multiple = true
        input.setAttribute("webkitdirectory", "")
        input.setAttribute("directory", "")
        input.style.display = "none"
        input.onchange = { _ ->
            val outcome = buildSourceFromInput(input)
            document.body?.removeChild(input)
            callback.value(outcome)
        }
        document.body?.appendChild(input)
        input.click()
    }
}

actual fun Modifier.archiveDropTarget(
    enabled: Boolean,
    onResult: (ArchivePickOutcome) -> Unit,
): Modifier = this // Folder drag-and-drop is not wired on web; use the button.

private fun buildSourceFromInput(input: HTMLInputElement): ArchivePickOutcome {
    val list = input.files
    if (list == null || list.length == 0) return ArchivePickOutcome.Cancelled

    val raw = HashMap<String, File>(list.length)
    for (i in 0 until list.length) {
        val file = list[i] ?: continue
        val rel = (file.unsafeRelativePath()).replace('\\', '/')
        if (rel.isNotEmpty()) raw[rel] = file
    }
    if (raw.isEmpty()) return ArchivePickOutcome.Cancelled

    val marker = "messages/index-messages.html"
    val prefix = raw.keys.firstOrNull { it.endsWith(marker) }?.removeSuffix(marker)
        ?: raw.keys.firstOrNull { it.endsWith("index.html") }?.removeSuffix("index.html")
        ?: ""
    val rebased = raw.entries
        .filter { it.key.startsWith(prefix) }
        .associate { it.key.removePrefix(prefix) to it.value }

    val name = prefix.trimEnd('/').substringAfterLast('/').ifEmpty { "Архив VK" }
    return ArchivePickOutcome.Success(WebArchiveSource(name, rebased))
}

private fun File.unsafeRelativePath(): String =
    (this as DirFile).webkitRelativePath

private class WebArchiveSource(
    override val displayName: String,
    private val files: Map<String, File>,
) : ArchiveSource {

    override suspend fun exists(path: String): Boolean = files.containsKey(path)

    override suspend fun readBytes(path: String): ByteArray? {
        val file = files[path] ?: return null
        return readFileBytes(file)
    }

    override suspend fun listDirs(path: String): List<String> {
        val prefix = if (path.isEmpty()) "" else "$path/"
        return files.keys.asSequence()
            .filter { it.startsWith(prefix) }
            .mapNotNull {
                val rest = it.removePrefix(prefix)
                val seg = rest.substringBefore('/')
                if (seg.isNotEmpty() && rest.contains('/')) seg else null
            }
            .distinct()
            .toList()
    }
}

private suspend fun readFileBytes(file: File): ByteArray =
    suspendCancellableCoroutine { cont ->
        val reader = FileReader()
        reader.onload = { _ ->
            val buffer = reader.result as ArrayBuffer
            val view = Int8Array(buffer)
            val bytes = ByteArray(view.length) { index -> view[index] }
            cont.resume(bytes)
        }
        reader.onerror = { _ ->
            cont.resumeWithException(RuntimeException("Не удалось прочитать файл из архива"))
        }
        reader.readAsArrayBuffer(file)
    }
