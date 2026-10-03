package app.stackd.core.cache

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class DiskSnapshotsTest {
    @Serializable
    data class Snap(val name: String, val xp: Long)

    @Test
    fun roundTripOverwriteAndClear() = runBlocking {
        val dir = Files.createTempDirectory("snap").toFile()
        val s = DiskSnapshots(dir)
        val key = "dashboard:user-1"

        assertNull(s.read(key, Snap.serializer()))
        s.write(key, Snap.serializer(), Snap("a", 1))
        s.write(key, Snap.serializer(), Snap("b", 2)) // overwrite must replace, not fail
        assertEquals(Snap("b", 2), s.read(key, Snap.serializer()))

        // A different shape (older app version) reads as a miss, not a crash.
        java.io.File(dir, "dashboard_user-1.json").writeText("{\"unexpected\":true}")
        assertNull(s.read(key, Snap.serializer()))

        s.write(key, Snap.serializer(), Snap("c", 3))
        s.clear()
        assertNull(s.read(key, Snap.serializer()))
    }
}
