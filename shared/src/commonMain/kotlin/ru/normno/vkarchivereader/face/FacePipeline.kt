package ru.normno.vkarchivereader.face

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import ru.normno.vkarchivereader.domain.model.MediaItem

data class FaceProgress(
    val processedImages: Int,
    val totalImages: Int,
    val faces: Int,
    val groups: Int,
)

/**
 * Orchestrates local face grouping: downloads each image to memory (never saved),
 * detects faces, clusters them online and persists only links + group assignment.
 */
class FacePipeline(
    private val analyzer: FaceAnalyzer,
    private val store: FaceStore,
    private val download: suspend (String) -> ByteArray?,
) {
    suspend fun process(
        images: List<MediaItem>,
        onProgress: (FaceProgress) -> Unit = {},
    ) {
        val clusterer = OnlineFaceClusterer()
        val faces = ArrayList<StoredFace>()
        // De-duplicate identical URLs so a photo isn't analyzed twice.
        val seen = HashSet<String>()
        var downloaded = 0
        var processed = 0
        // First analyzer failure (e.g. broken model) — surfaced only if nothing
        // was detected, so one corrupt image doesn't abort the whole run.
        var analyzerError: Throwable? = null

        // Downloads dominate the runtime (network round-trips to the VK CDN), so
        // fetch each batch concurrently to overlap that latency. Detection and
        // clustering stay strictly sequential: the online clusterer is stateful
        // and order-dependent, and the native analyzer isn't safe to call
        // concurrently. Batching also bounds peak memory to DOWNLOAD_CONCURRENCY
        // images at a time.
        images.chunked(DOWNLOAD_CONCURRENCY).forEach { batch ->
            currentCoroutineContext().ensureActive()
            // Decide what to fetch sequentially — HashSet isn't thread-safe — so
            // duplicate URLs within/across batches are downloaded at most once.
            val plan = batch.map { item -> item to seen.add(item.url) }
            val batchBytes = coroutineScope {
                plan.map { (item, isNew) ->
                    // Expired/unavailable VK links are expected — tolerate them.
                    async { if (isNew) runCatching { download(item.url) }.getOrNull() else null }
                }.awaitAll()
            }

            plan.forEachIndexed { i, (item, _) ->
                val bytes = batchBytes[i]
                if (bytes != null) {
                    downloaded++
                    try {
                        val detected = analyzer.detect(bytes)
                        for (face in detected) {
                            val cluster = clusterer.assign(face.embedding)
                            faces.add(StoredFace(item.url, item.chatPeerId, item.chatTitle, cluster.toLong()))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        if (analyzerError == null) analyzerError = e
                    }
                }
                processed++
                onProgress(
                    FaceProgress(
                        processedImages = processed,
                        totalImages = images.size,
                        faces = faces.size,
                        groups = clusterer.clusterCount,
                    )
                )
            }
            yield()
        }

        // Nothing detected — distinguish a real failure from "no faces / no images".
        if (faces.isEmpty()) {
            analyzerError?.let { throw it }
            check(downloaded > 0) {
                "Ни одно фото не удалось загрузить (${images.size} ссылок). " +
                    "Скорее всего ссылки архива VK устарели."
            }
        }

        val groups = (0 until clusterer.clusterCount).map {
            StoredGroup(it.toLong(), "Группа ${it + 1}")
        }
        store.save(groups, faces)
    }

    private companion object {
        // How many images to download in parallel per batch. Enough to hide
        // network latency without flooding the CDN or holding many images at once.
        const val DOWNLOAD_CONCURRENCY = 6
    }
}
