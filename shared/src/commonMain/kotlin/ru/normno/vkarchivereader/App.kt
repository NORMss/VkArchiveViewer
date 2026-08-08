package ru.normno.vkarchivereader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import kotlinx.coroutines.launch
import ru.normno.vkarchivereader.download.fileNameFor
import ru.normno.vkarchivereader.download.rememberMediaDownloader
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toPath
import org.koin.compose.KoinApplication
import org.koin.compose.getKoin
import ru.normno.vkarchivereader.core.ImageCacheStore
import ru.normno.vkarchivereader.di.appModule
import ru.normno.vkarchivereader.domain.model.AttachmentType
import ru.normno.vkarchivereader.domain.model.MediaItem
import ru.normno.vkarchivereader.presentation.archive.ArchiveUiState
import ru.normno.vkarchivereader.presentation.archive.ArchiveViewModel
import ru.normno.vkarchivereader.presentation.archive.ChatListScreen
import ru.normno.vkarchivereader.presentation.components.FullscreenMediaViewer
import ru.normno.vkarchivereader.presentation.conversation.ConversationScreen
import ru.normno.vkarchivereader.presentation.face.FaceGroupsScreen
import ru.normno.vkarchivereader.presentation.media.MediaGalleryScreen
import ru.normno.vkarchivereader.presentation.navigation.Screen
import ru.normno.vkarchivereader.presentation.welcome.WelcomeScreen

@Composable
@Preview
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .apply {
                // On-device disk cache for fast subsequent loads (not on web).
                ImageCacheStore.diskCacheDir()?.let { dir ->
                    diskCache {
                        DiskCache.Builder()
                            .directory(dir.toPath())
                            .maxSizeBytes(ImageCacheStore.maxBytes())
                            .build()
                    }
                }
            }
            .crossfade(true)
            .build()
    }

    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            KoinApplication(application = { modules(appModule) }) {
                AppContent()
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppContent() {
    val koin = getKoin()
    val viewModel: ArchiveViewModel = viewModel { ArchiveViewModel(koin.get()) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()

    // In-app navigation is a simple screen stack so the system back gesture pops
    // to the previous screen instead of closing the app on mobile.
    val backStack = remember { mutableStateListOf<Screen>(Screen.ChatList) }
    val screen = backStack.last()
    var fullscreenImage by remember { mutableStateOf<String?>(null) }

    val downloader = rememberMediaDownloader()
    val scope = rememberCoroutineScope()

    val loaded = uiState is ArchiveUiState.Loaded
    // Whenever we leave the loaded archive (closed/reset/error), collapse the
    // stack back to the chat list so a freshly opened archive starts clean.
    LaunchedEffect(loaded) {
        if (!loaded) {
            backStack.clear()
            backStack.add(Screen.ChatList)
            fullscreenImage = null
        }
    }

    fun navigate(target: Screen) { backStack.add(target) }
    fun popBack() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    // System back / predictive-back: close the viewer, else pop the stack, else
    // (at the root chat list) close the archive back to the welcome screen.
    BackHandler(enabled = loaded) {
        when {
            fullscreenImage != null -> fullscreenImage = null
            backStack.size > 1 -> popBack()
            else -> viewModel.reset()
        }
    }

    // Edge-to-edge: keep the app's content out from under the side/bottom system
    // bars and display cutouts. Each screen's TopAppBar applies the top inset
    // itself, and the fullscreen viewer below draws full-bleed on purpose.
    val contentInsets = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

    Box(Modifier.fillMaxSize()) {
        val loadedData = data
        Box(Modifier.fillMaxSize().windowInsetsPadding(contentInsets)) {
            when {
                uiState is ArchiveUiState.Loaded && loadedData != null -> when (val current = screen) {
                    is Screen.ChatList -> ChatListScreen(
                        data = loadedData,
                        viewModel = viewModel,
                        onOpenChat = { chat, messageId -> navigate(Screen.Conversation(chat, messageId)) },
                        onOpenMedia = { navigate(Screen.MediaGallery(null, null)) },
                        onClose = { viewModel.reset() },
                    )

                    is Screen.Conversation -> ConversationScreen(
                        chat = current.chat,
                        targetMessageId = current.targetMessageId,
                        onBack = { popBack() },
                        onOpenMedia = {
                            navigate(Screen.MediaGallery(current.chat.peerId, current.chat.title))
                        },
                        onOpenImage = { fullscreenImage = it },
                    )

                    is Screen.MediaGallery -> MediaGalleryScreen(
                        data = loadedData,
                        peerId = current.peerId,
                        chatTitle = current.chatTitle,
                        onBack = { popBack() },
                        onOpenFaces = { images ->
                            navigate(Screen.FaceGroups(images, loadedData.ownerId ?: loadedData.displayName))
                        },
                    )

                    is Screen.FaceGroups -> FaceGroupsScreen(
                        images = current.images,
                        archiveId = current.archiveId,
                        onBack = { popBack() },
                    )
                }

                else -> WelcomeScreen(
                    state = uiState,
                    onResult = viewModel::onArchivePicked,
                )
            }
        }

        fullscreenImage?.let { url ->
            FullscreenMediaViewer(
                items = listOf(MediaItem(url, AttachmentType.PHOTO, "", "", null)),
                startIndex = 0,
                onClose = { fullscreenImage = null },
                onDownload = { item ->
                    scope.launch { downloader.download(item.url, fileNameFor(item.url, 0, "photo")) }
                },
            )
        }
    }
}
