package ru.normno.vkarchivereader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

@Composable
private fun AppContent() {
    val koin = getKoin()
    val viewModel: ArchiveViewModel = viewModel { ArchiveViewModel(koin.get()) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf<Screen>(Screen.ChatList) }
    var fullscreenImage by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        val loadedData = data
        when {
            uiState is ArchiveUiState.Loaded && loadedData != null -> when (val current = screen) {
                is Screen.ChatList -> ChatListScreen(
                    data = loadedData,
                    viewModel = viewModel,
                    onOpenChat = { chat, messageId -> screen = Screen.Conversation(chat, messageId) },
                    onOpenMedia = { screen = Screen.MediaGallery(null, null) },
                    onClose = {
                        screen = Screen.ChatList
                        viewModel.reset()
                    },
                )

                is Screen.Conversation -> ConversationScreen(
                    chat = current.chat,
                    targetMessageId = current.targetMessageId,
                    onBack = { screen = Screen.ChatList },
                    onOpenMedia = {
                        screen = Screen.MediaGallery(current.chat.peerId, current.chat.title)
                    },
                    onOpenImage = { fullscreenImage = it },
                )

                is Screen.MediaGallery -> MediaGalleryScreen(
                    data = loadedData,
                    peerId = current.peerId,
                    chatTitle = current.chatTitle,
                    onBack = {
                        screen = if (current.peerId != null) {
                            loadedData.chats.firstOrNull { it.peerId == current.peerId }
                                ?.let { Screen.Conversation(it) } ?: Screen.ChatList
                        } else {
                            Screen.ChatList
                        }
                    },
                    onOpenFaces = { images -> screen = Screen.FaceGroups(images) },
                )

                is Screen.FaceGroups -> FaceGroupsScreen(
                    images = current.images,
                    onBack = { screen = Screen.MediaGallery(null, null) },
                )
            }

            else -> WelcomeScreen(
                state = uiState,
                onResult = viewModel::onArchivePicked,
            )
        }

        fullscreenImage?.let { url ->
            FullscreenMediaViewer(
                items = listOf(MediaItem(url, AttachmentType.PHOTO, "", "", null)),
                startIndex = 0,
                onClose = { fullscreenImage = null },
            )
        }
    }
}
