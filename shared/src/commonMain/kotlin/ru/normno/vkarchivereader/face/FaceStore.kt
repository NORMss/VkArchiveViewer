package ru.normno.vkarchivereader.face

import kotlinx.coroutines.flow.Flow

/**
 * Local persistence of face-grouping results. Only links (photo URL), the chat
 * of origin and the group assignment are stored — never the image itself.
 */
interface FaceStore {
    /** Replace all stored faces/groups with a fresh clustering result. */
    suspend fun save(groups: List<StoredGroup>, faces: List<StoredFace>)

    /** Rename a group (e.g. to "Имя Фамилия"). */
    suspend fun renameGroup(groupId: Long, name: String)

    /** Remove all stored faces and groups. */
    suspend fun clear()

    /** Reactive list of groups (largest first), for the UI. */
    fun observeGroups(): Flow<List<FaceGroup>>

    /** Photos in a group. */
    suspend fun photosOf(groupId: Long): List<GroupPhoto>
}
