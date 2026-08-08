package ru.normno.vkarchivereader.face

import kotlinx.coroutines.flow.Flow

/**
 * Local persistence of face-grouping results. Only links (photo URL), the chat
 * of origin and the group assignment are stored — never the image itself.
 */
interface FaceStore {
    /**
     * Scope every subsequent read/write to a single archive (identified by its
     * owner id or name). Groups produced for a different archive stay hidden, so
     * opening a new archive never shows the previous person's photos.
     */
    suspend fun setArchive(archiveId: String)

    /** Replace the current archive's faces/groups with a fresh clustering result. */
    suspend fun save(groups: List<StoredGroup>, faces: List<StoredFace>)

    /** Rename a group (e.g. to "Имя Фамилия"). */
    suspend fun renameGroup(groupId: Long, name: String)

    /** Remove the current archive's stored faces and groups. */
    suspend fun clear()

    /** Reactive list of the current archive's groups (largest first), for the UI. */
    fun observeGroups(): Flow<List<FaceGroup>>

    /** Photos in a group of the current archive. */
    suspend fun photosOf(groupId: Long): List<GroupPhoto>
}
