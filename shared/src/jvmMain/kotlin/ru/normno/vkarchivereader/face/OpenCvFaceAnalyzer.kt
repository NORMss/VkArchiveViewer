package ru.normno.vkarchivereader.face

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.PointerScope
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgcodecs
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN
import org.bytedeco.opencv.opencv_objdetect.FaceRecognizerSF
import java.io.File
import java.net.URL

/**
 * Desktop face analyzer using OpenCV's YuNet (detection) + SFace (recognition),
 * both tiny ONNX models from the OpenCV Zoo. Runs fully on-device. Models are
 * downloaded once into [modelsDir] and cached.
 */
class OpenCvFaceAnalyzer(private val modelsDir: File) : FaceAnalyzer {

    override val embeddingSize: Int = 128

    private val initMutex = Mutex()
    private var detector: FaceDetectorYN? = null
    private var recognizer: FaceRecognizerSF? = null

    private companion object {
        // NOTE: opencv_zoo stores these models in Git LFS. The plain
        // raw.githubusercontent.com URL returns a ~130-byte LFS *pointer* file,
        // not the model — which OpenCV then fails to parse (silently, as 0 faces).
        // The media.githubusercontent.com/media/... endpoint resolves LFS and
        // returns the real binary.
        const val YUNET_URL =
            "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx"
        const val YUNET_MIN_BYTES = 100_000L
        const val SFACE_URL =
            "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx"
        const val SFACE_MIN_BYTES = 1_000_000L
        const val LFS_POINTER_PREFIX = "version https://git-lfs"
    }

    private suspend fun ensureReady() {
        if (detector != null && recognizer != null) return
        initMutex.withLock {
            if (detector != null && recognizer != null) return
            modelsDir.mkdirs()
            val yunet = ensureModel("yunet.onnx", YUNET_URL, YUNET_MIN_BYTES)
            val sface = ensureModel("sface.onnx", SFACE_URL, SFACE_MIN_BYTES)
            // backend_id = 0 (default), target_id = 0 (CPU).
            detector = FaceDetectorYN.create(
                BytePointer(yunet.absolutePath), BytePointer(""), Size(320, 320),
                0.6f, 0.3f, 5000, 0, 0,
            )
            recognizer = FaceRecognizerSF.create(BytePointer(sface.absolutePath), BytePointer(""))
        }
    }

    private fun ensureModel(name: String, url: String, minBytes: Long): File {
        val file = File(modelsDir, name)
        if (file.isValidModel(minBytes)) return file
        file.delete()
        URL(url).openStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        check(file.isValidModel(minBytes)) {
            "Скачанная модель $name повреждена (${file.length()} байт). " +
                "Похоже, вернулся LFS-указатель, а не сам файл. Удалите ~/.vkarchivereader/models и повторите."
        }
        return file
    }

    /** Rejects empty files and Git LFS pointer stubs cached by an earlier bad URL. */
    private fun File.isValidModel(minBytes: Long): Boolean {
        if (!isFile || length() < minBytes) return false
        val head = inputStream().use { it.readNBytes(LFS_POINTER_PREFIX.length) }
        return String(head, Charsets.US_ASCII) != LFS_POINTER_PREFIX
    }

    override suspend fun detect(imageBytes: ByteArray): List<DetectedFace> =
        withContext(Dispatchers.IO) {
            ensureReady()
            val det = detector ?: return@withContext emptyList()
            val rec = recognizer ?: return@withContext emptyList()

            PointerScope().use {
                val buf = Mat(1, imageBytes.size, opencv_core.CV_8UC1, BytePointer(*imageBytes))
                val img = opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR)
                if (img.empty()) return@use emptyList()

                det.setInputSize(Size(img.cols(), img.rows()))
                val faces = Mat()
                det.detect(img, faces)

                val result = ArrayList<DetectedFace>(faces.rows())
                for (i in 0 until faces.rows()) {
                    val row = faces.row(i)
                    val aligned = Mat()
                    rec.alignCrop(img, row, aligned)
                    val feature = Mat()
                    rec.feature(aligned, feature)
                    val indexer = feature.createIndexer<FloatIndexer>()
                    val embedding = FloatArray(feature.cols()) { indexer.get(0L, it.toLong()) }
                    indexer.release()
                    result.add(DetectedFace(embedding))
                }
                result
            }
        }

    override fun close() {
        detector?.close()
        recognizer?.close()
        detector = null
        recognizer = null
    }
}
