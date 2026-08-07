package ru.normno.vkarchivereader.face

/**
 * Detects faces in an image and returns an embedding (feature vector) per face.
 * Implemented per platform with a local, on-device model (no data leaves the
 * device). Returns an empty list when no faces are found or the image is bad.
 */
interface FaceAnalyzer {
    /** Number of dimensions of the produced embeddings (SFace = 128). */
    val embeddingSize: Int

    /** Detect faces in raw image bytes and return their embeddings. */
    suspend fun detect(imageBytes: ByteArray): List<DetectedFace>

    /** Release native resources. */
    fun close()
}
