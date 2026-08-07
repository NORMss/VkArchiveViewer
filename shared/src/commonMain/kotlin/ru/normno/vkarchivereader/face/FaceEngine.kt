package ru.normno.vkarchivereader.face

/**
 * Bundles the platform face-recognition implementation. Created via
 * [createFaceEngine]; null on platforms where local face recognition is not
 * available (currently everything except desktop/JVM).
 */
class FaceEngine(
    val store: FaceStore,
    val pipeline: FacePipeline,
    val analyzer: FaceAnalyzer,
)

/** Returns the platform face engine, or null if unsupported here. */
expect fun createFaceEngine(): FaceEngine?
