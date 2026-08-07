package ru.normno.vkarchivereader.data.source

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Result of the platform "open archive" interaction. */
sealed interface ArchivePickOutcome {
    data class Success(val source: ArchiveSource) : ArchivePickOutcome
    data class Failure(val message: String) : ArchivePickOutcome
    data object Cancelled : ArchivePickOutcome
}

/**
 * Returns a launcher that opens the platform file/folder picker and reports an
 * [ArchivePickOutcome]. Implemented per platform; unsupported platforms return a
 * launcher that reports [ArchivePickOutcome.Failure].
 */
@Composable
expect fun rememberArchiveChooser(onResult: (ArchivePickOutcome) -> Unit): () -> Unit

/**
 * Adds drag-and-drop support for an archive folder/zip. No-op on platforms that
 * do not support file drops.
 */
expect fun Modifier.archiveDropTarget(
    enabled: Boolean,
    onResult: (ArchivePickOutcome) -> Unit,
): Modifier
