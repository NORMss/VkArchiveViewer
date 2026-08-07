package ru.normno.vkarchivereader.data.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private const val UNSUPPORTED =
    "Выбор архива на iOS пока не реализован."

@Composable
actual fun rememberArchiveChooser(onResult: (ArchivePickOutcome) -> Unit): () -> Unit =
    { onResult(ArchivePickOutcome.Failure(UNSUPPORTED)) }

actual fun Modifier.archiveDropTarget(
    enabled: Boolean,
    onResult: (ArchivePickOutcome) -> Unit,
): Modifier = this
