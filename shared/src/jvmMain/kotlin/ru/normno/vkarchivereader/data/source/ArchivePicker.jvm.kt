package ru.normno.vkarchivereader.data.source

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberArchiveChooser(onResult: (ArchivePickOutcome) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val selectedFile = chooseFileOnEdt()
                    selectedFile?.let { sourceFromFile(it) }
                }.fold(
                    onSuccess = { src ->
                        if (src == null) ArchivePickOutcome.Cancelled
                        else ArchivePickOutcome.Success(src)
                    },
                    onFailure = { ArchivePickOutcome.Failure(it.message ?: "Ошибка открытия архива") },
                )
            }
            onResult(outcome)
        }
    }
}

actual fun Modifier.archiveDropTarget(
    enabled: Boolean,
    onResult: (ArchivePickOutcome) -> Unit,
): Modifier {
    if (!enabled) return this
    val target = object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val files = droppedFiles(event)
            val file = files.firstOrNull() ?: return false
            return try {
                onResult(ArchivePickOutcome.Success(sourceFromFile(file)))
                true
            } catch (e: Throwable) {
                onResult(ArchivePickOutcome.Failure(e.message ?: "Ошибка открытия архива"))
                false
            }
        }
    }
    return this.dragAndDropTarget(
        shouldStartDragAndDrop = { true },
        target = target,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("UNCHECKED_CAST")
private fun droppedFiles(event: DragAndDropEvent): List<File> = runCatching {
    val transferable = event.awtTransferable
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        (transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>)
    } else {
        emptyList()
    }
}.getOrElse { emptyList() }

/**
 * Показывает модальный [JFileChooser] строго на потоке диспетчеризации событий AWT (EDT).
 *
 * Swing-диалоги обязаны создаваться и отображаться на EDT. Compose Desktop сам владеет EDT,
 * поэтому вызов [JFileChooser.showOpenDialog] из фонового потока (Dispatchers.IO) оставляет
 * внутреннее модальное состояние Swing в рассогласованном виде и приводит к дедлоку EDT при
 * повторном открытии диалога — приложение зависает. [SwingUtilities.invokeAndWait] выполняет
 * диалог на EDT и блокирует вызывающий фоновый поток до его закрытия.
 */
private fun chooseFileOnEdt(): File? {
    var selected: File? = null
    val showDialog = Runnable {
        val chooser = JFileChooser().apply {
            dialogTitle = "Выберите папку архива VK или zip-файл"
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
            fileFilter = FileNameExtensionFilter("Архив VK (папка или .zip)", "zip", "html")
            isAcceptAllFileFilterUsed = true
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            selected = chooser.selectedFile
        }
    }
    if (SwingUtilities.isEventDispatchThread()) {
        showDialog.run()
    } else {
        SwingUtilities.invokeAndWait(showDialog)
    }
    return selected
}

private fun sourceFromFile(file: File): ArchiveSource =
    if (file.isFile && file.extension.equals("zip", ignoreCase = true)) {
        zipToArchiveSource(file)
    } else {
        resolveDirectorySource(file)
    }
