package ru.normno.vkarchivereader.face

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import java.io.File

private val engine: FaceEngine? by lazy { buildEngine() }

actual fun createFaceEngine(): FaceEngine? = engine

private fun buildEngine(): FaceEngine {
    val appDir = File(System.getProperty("user.home"), ".vkarchivereader").apply { mkdirs() }
    val store = SqliteFaceStore(File(appDir, "faces.db"))
    val analyzer = OpenCvFaceAnalyzer(File(appDir, "models"))

    val client = HttpClient(CIO)
    val download: suspend (String) -> ByteArray? = { url ->
        runCatching { client.get(url).body<ByteArray>() }.getOrNull()
    }

    return FaceEngine(store, FacePipeline(analyzer, store, download), analyzer)
}
