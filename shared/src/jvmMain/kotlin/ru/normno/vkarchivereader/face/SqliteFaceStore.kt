package ru.normno.vkarchivereader.face

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Desktop face store backed by a local SQLite database (only links + groups). */
class SqliteFaceStore(dbFile: File) : FaceStore {

    private val connection: Connection =
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    private val mutex = Mutex()
    private val _groups = MutableStateFlow<List<FaceGroup>>(emptyList())

    /** The archive all reads/writes are scoped to; empty until [setArchive]. */
    @Volatile
    private var archiveId: String = ""

    init {
        // The face DB gained an archive_id column to keep archives separate. Old
        // caches from before that lack the column — recreate them once (the DB is
        // only a cache, so nothing important is lost).
        if (tableExists("face") && !faceHasArchiveColumn()) {
            connection.createStatement().use {
                it.executeUpdate("DROP TABLE IF EXISTS face")
                it.executeUpdate("DROP TABLE IF EXISTS face_group")
            }
        }
        connection.createStatement().use { st ->
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS face_group (
                    archive_id TEXT NOT NULL,
                    id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    PRIMARY KEY(archive_id, id)
                )""".trimIndent()
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS face (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    archive_id TEXT NOT NULL,
                    url TEXT NOT NULL,
                    chat_peer_id TEXT NOT NULL,
                    chat_title TEXT NOT NULL,
                    group_id INTEGER NOT NULL
                )""".trimIndent()
            )
        }
    }

    override suspend fun setArchive(archiveId: String) = withContext(Dispatchers.IO) {
        this@SqliteFaceStore.archiveId = archiveId
        _groups.value = mutex.withLock { readGroups() }
    }

    override suspend fun save(groups: List<StoredGroup>, faces: List<StoredFace>) =
        withContext(Dispatchers.IO) {
            val archive = archiveId
            mutex.withLock {
                connection.autoCommit = false
                try {
                    connection.prepareStatement("DELETE FROM face WHERE archive_id = ?").use {
                        it.setString(1, archive); it.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM face_group WHERE archive_id = ?").use {
                        it.setString(1, archive); it.executeUpdate()
                    }
                    connection.prepareStatement(
                        "INSERT INTO face_group(archive_id, id, name) VALUES(?, ?, ?)"
                    ).use { ps ->
                        for (g in groups) {
                            ps.setString(1, archive)
                            ps.setLong(2, g.id)
                            ps.setString(3, g.name)
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                    connection.prepareStatement(
                        "INSERT INTO face(archive_id, url, chat_peer_id, chat_title, group_id) VALUES(?, ?, ?, ?, ?)"
                    ).use { ps ->
                        for (f in faces) {
                            ps.setString(1, archive)
                            ps.setString(2, f.url)
                            ps.setString(3, f.chatPeerId)
                            ps.setString(4, f.chatTitle)
                            ps.setLong(5, f.groupId)
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                    connection.commit()
                } catch (e: Throwable) {
                    connection.rollback()
                    throw e
                } finally {
                    connection.autoCommit = true
                }
            }
            _groups.value = mutex.withLock { readGroups() }
        }

    override suspend fun renameGroup(groupId: Long, name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection.prepareStatement(
                "UPDATE face_group SET name = ? WHERE id = ? AND archive_id = ?"
            ).use { ps ->
                ps.setString(1, name)
                ps.setLong(2, groupId)
                ps.setString(3, archiveId)
                ps.executeUpdate()
            }
        }
        _groups.value = mutex.withLock { readGroups() }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        val archive = archiveId
        mutex.withLock {
            connection.prepareStatement("DELETE FROM face WHERE archive_id = ?").use {
                it.setString(1, archive); it.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM face_group WHERE archive_id = ?").use {
                it.setString(1, archive); it.executeUpdate()
            }
        }
        _groups.value = emptyList()
    }

    override fun observeGroups(): StateFlow<List<FaceGroup>> = _groups.asStateFlow()

    override suspend fun photosOf(groupId: Long): List<GroupPhoto> = withContext(Dispatchers.IO) {
        val out = ArrayList<GroupPhoto>()
        mutex.withLock {
            connection.prepareStatement(
                "SELECT id, url, chat_peer_id, chat_title FROM face WHERE group_id = ? AND archive_id = ? ORDER BY id"
            ).use { ps ->
                ps.setLong(1, groupId)
                ps.setString(2, archiveId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        out.add(
                            GroupPhoto(
                                faceId = rs.getLong(1),
                                url = rs.getString(2),
                                chatPeerId = rs.getString(3),
                                chatTitle = rs.getString(4),
                            )
                        )
                    }
                }
            }
        }
        out
    }

    /** Groups of the currently-scoped archive. Caller must hold [mutex]. */
    private fun readGroups(): List<FaceGroup> {
        val out = ArrayList<FaceGroup>()
        connection.prepareStatement(
            """SELECT g.id, g.name, COUNT(f.id) AS cnt, MIN(f.url) AS cover
               FROM face_group g LEFT JOIN face f ON f.group_id = g.id AND f.archive_id = g.archive_id
               WHERE g.archive_id = ?
               GROUP BY g.id, g.name
               ORDER BY cnt DESC""".trimIndent()
        ).use { ps ->
            ps.setString(1, archiveId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(
                        FaceGroup(
                            id = rs.getLong("id"),
                            name = rs.getString("name"),
                            coverUrl = rs.getString("cover"),
                            photoCount = rs.getInt("cnt"),
                        )
                    )
                }
            }
        }
        return out
    }

    private fun tableExists(name: String): Boolean =
        connection.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?"
        ).use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { it.next() }
        }

    private fun faceHasArchiveColumn(): Boolean =
        connection.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(face)").use { rs ->
                generateSequence { if (rs.next()) rs.getString("name") else null }
                    .any { it == "archive_id" }
            }
        }
}
