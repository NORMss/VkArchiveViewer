package ru.normno.vkarchivereader

import kotlinx.coroutines.runBlocking
import ru.normno.vkarchivereader.face.OnlineFaceClusterer
import ru.normno.vkarchivereader.face.SqliteFaceStore
import ru.normno.vkarchivereader.face.StoredFace
import ru.normno.vkarchivereader.face.StoredGroup
import ru.normno.vkarchivereader.face.cosineSimilarity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceCoreTest {

    @Test
    fun cosineBasics() {
        assertTrue(cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f)) > 0.99f)
        assertTrue(cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)) < 0.01f)
    }

    @Test
    fun clustersTwoPeople() {
        val clusterer = OnlineFaceClusterer()
        // Person A near (1,0,0), person B near (0,1,0), with small noise.
        val a1 = clusterer.assign(floatArrayOf(0.98f, 0.05f, 0.02f))
        val a2 = clusterer.assign(floatArrayOf(0.97f, 0.10f, 0.00f))
        val b1 = clusterer.assign(floatArrayOf(0.03f, 0.99f, 0.01f))
        val b2 = clusterer.assign(floatArrayOf(0.00f, 0.95f, 0.08f))

        assertEquals(2, clusterer.clusterCount, "expected exactly two people")
        assertEquals(a1, a2, "person A photos should share a cluster")
        assertEquals(b1, b2, "person B photos should share a cluster")
        assertTrue(a1 != b1, "different people must be different clusters")
    }

    @Test
    fun sqliteStoreRoundTrip() = runBlocking {
        val dbFile = File.createTempFile("faces-test", ".db").apply { deleteOnExit() }
        val store = SqliteFaceStore(dbFile)

        store.setArchive("archive-1")
        store.save(
            groups = listOf(StoredGroup(0, "Группа 1"), StoredGroup(1, "Группа 2")),
            faces = listOf(
                StoredFace("http://a/1.jpg", "-100", "Chat A", 0),
                StoredFace("http://a/2.jpg", "-100", "Chat A", 0),
                StoredFace("http://b/1.jpg", "200", "Chat B", 1),
            ),
        )

        val groups = store.observeGroups().value
        assertEquals(2, groups.size)
        // Largest group first.
        assertEquals(2, groups.first().photoCount)
        assertTrue(groups.first().coverUrl != null)

        val photos = store.photosOf(0)
        assertEquals(2, photos.size)

        store.renameGroup(0, "Иван Иванов")
        assertEquals("Иван Иванов", store.observeGroups().value.first { it.id == 0L }.name)

        // Opening a different archive must not surface the first archive's groups.
        store.setArchive("archive-2")
        assertTrue(store.observeGroups().value.isEmpty())
        store.setArchive("archive-1")
        assertEquals(2, store.observeGroups().value.size)

        store.clear()
        assertTrue(store.observeGroups().value.isEmpty())
        println("[faceTest] store round-trip OK")
    }
}
