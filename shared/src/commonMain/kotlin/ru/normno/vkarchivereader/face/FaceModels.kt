package ru.normno.vkarchivereader.face

/** A cluster of faces believed to be the same person. */
data class FaceGroup(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val photoCount: Int,
)

/** A photo that contains a face belonging to a group. */
data class GroupPhoto(
    val faceId: Long,
    val url: String,
    val chatPeerId: String,
    val chatTitle: String,
)

/** One detected face: its embedding plus where it came from. */
data class DetectedFace(val embedding: FloatArray) {
    override fun equals(other: Any?) =
        other is DetectedFace && embedding.contentEquals(other.embedding)
    override fun hashCode() = embedding.contentHashCode()
}

/** A row to persist after clustering. */
data class StoredFace(
    val url: String,
    val chatPeerId: String,
    val chatTitle: String,
    val groupId: Long,
)

data class StoredGroup(val id: Long, val name: String)
