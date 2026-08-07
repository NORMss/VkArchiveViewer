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

    init {
        connection.createStatement().use { st ->
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS face_group (id INTEGER PRIMARY KEY, name TEXT NOT NULL)"
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS face (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    url TEXT NOT NULL,
                    chat_peer_id TEXT NOT NULL,
                    chat_title TEXT NOT NULL,
                    group_id INTEGER NOT NULL
                )""".trimIndent()
            )
        }
        _groups.value = readGroups()
    }

    override suspend fun save(groups: List<StoredGroup>, faces: List<StoredFace>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                connection.autoCommit = false
                try {
                    connection.createStatement().use {
                        it.executeUpdate("DELETE FROM face")
                        it.executeUpdate("DELETE FROM face_group")
                    }
                    connection.prepareStatement("INSERT INTO face_group(id, name) VALUES(?, ?)").use { ps ->
                        for (g in groups) {
                            ps.setLong(1, g.id)
                            ps.setString(2, g.name)
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                    connection.prepareStatement(
                        "INSERT INTO face(url, chat_peer_id, chat_title, group_id) VALUES(?, ?, ?, ?)"
                    ).use { ps ->
                        for (f in faces) {
                            ps.setString(1, f.url)
                            ps.setString(2, f.chatPeerId)
                            ps.setString(3, f.chatTitle)
                            ps.setLong(4, f.groupId)
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
            _groups.value = readGroups()
        }

    override suspend fun renameGroup(groupId: Long, name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection.prepareStatement("UPDATE face_group SET name = ? WHERE id = ?").use { ps ->
                ps.setString(1, name)
                ps.setLong(2, groupId)
                ps.executeUpdate()
            }
        }
        _groups.value = readGroups()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            connection.createStatement().use {
                it.executeUpdate("DELETE FROM face")
                it.executeUpdate("DELETE FROM face_group")
            }
        }
        _groups.value = emptyList()
    }

    override fun observeGroups(): StateFlow<List<FaceGroup>> = _groups.asStateFlow()

    override suspend fun photosOf(groupId: Long): List<GroupPhoto> = withContext(Dispatchers.IO) {
        val out = ArrayList<GroupPhoto>()
        connection.prepareStatement(
            "SELECT id, url, chat_peer_id, chat_title FROM face WHERE group_id = ? ORDER BY id"
        ).use { ps ->
            ps.setLong(1, groupId)
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
        out
    }

    private fun readGroups(): List<FaceGroup> {
        val out = ArrayList<FaceGroup>()
        connection.createStatement().use { st ->
            st.executeQuery(
                """SELECT g.id, g.name, COUNT(f.id) AS cnt, MIN(f.url) AS cover
                   FROM face_group g LEFT JOIN face f ON f.group_id = g.id
                   GROUP BY g.id, g.name
                   ORDER BY cnt DESC""".trimIndent()
            ).use { rs ->
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
}
