package ru.normno.vkarchivereader.presentation.face

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.normno.vkarchivereader.domain.model.MediaItem
import ru.normno.vkarchivereader.face.FaceGroup
import ru.normno.vkarchivereader.face.FaceProgress
import ru.normno.vkarchivereader.face.GroupPhoto
import ru.normno.vkarchivereader.face.createFaceEngine

sealed interface FaceProcessingState {
    data object Idle : FaceProcessingState
    data class Running(val progress: FaceProgress) : FaceProcessingState
    data object Done : FaceProcessingState
    data class Failed(val message: String) : FaceProcessingState
}

class FaceViewModel : ViewModel() {

    private val engine = createFaceEngine()
    val supported: Boolean get() = engine != null

    val groups: Flow<List<FaceGroup>> =
        engine?.store?.observeGroups() ?: flowOf(emptyList())

    private val _processing = MutableStateFlow<FaceProcessingState>(FaceProcessingState.Idle)
    val processing: StateFlow<FaceProcessingState> = _processing.asStateFlow()

    private var job: Job? = null

    fun process(images: List<MediaItem>) {
        val e = engine ?: return
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _processing.value = FaceProcessingState.Running(FaceProgress(0, images.size, 0, 0))
            try {
                withContext(Dispatchers.Default) {
                    e.pipeline.process(images) { progress ->
                        _processing.value = FaceProcessingState.Running(progress)
                    }
                }
                _processing.value = FaceProcessingState.Done
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _processing.value = FaceProcessingState.Failed(t.message ?: "Ошибка обработки")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _processing.value = FaceProcessingState.Idle
    }

    fun rename(groupId: Long, name: String) {
        val e = engine ?: return
        viewModelScope.launch { e.store.renameGroup(groupId, name) }
    }

    fun clear() {
        val e = engine ?: return
        viewModelScope.launch { e.store.clear() }
    }

    suspend fun photosOf(groupId: Long): List<GroupPhoto> =
        engine?.store?.photosOf(groupId) ?: emptyList()

    override fun onCleared() {
        job?.cancel()
    }
}
