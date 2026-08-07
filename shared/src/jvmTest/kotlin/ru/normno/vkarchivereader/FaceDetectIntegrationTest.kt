package ru.normno.vkarchivereader

import kotlinx.coroutines.runBlocking
import ru.normno.vkarchivereader.face.OpenCvFaceAnalyzer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end check of the desktop analyzer: downloads the real YuNet+SFace
 * models (validating the Git LFS media URL fix) and runs detection on a known
 * portrait. Hits the network — disabled by default; enable with
 * `-DrunFaceIntegration=true`.
 */
class FaceDetectIntegrationTest {

    @Test
    fun detectsAFaceOnRealPortrait() {
        if (System.getProperty("runFaceIntegration") != "true") {
            println("[faceTest] integration test skipped (set -DrunFaceIntegration=true to run)")
            return
        }
        val image = File("src/jvmTest/resources/face.jpg")
        assertTrue(image.isFile, "test image missing: ${image.absolutePath}")

        val modelsDir = File(System.getProperty("java.io.tmpdir"), "vkar-face-models-test")
        val analyzer = OpenCvFaceAnalyzer(modelsDir)
        try {
            val faces = runBlocking { analyzer.detect(image.readBytes()) }
            println("[faceTest] detected ${faces.size} face(s); embeddingSize=${analyzer.embeddingSize}")
            assertTrue(faces.isNotEmpty(), "expected at least one face on the portrait")
            assertTrue(
                faces.all { it.embedding.size == analyzer.embeddingSize },
                "embedding length must equal ${analyzer.embeddingSize}",
            )
        } finally {
            analyzer.close()
        }
    }
}
