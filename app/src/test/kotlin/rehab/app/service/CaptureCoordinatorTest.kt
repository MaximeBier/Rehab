package rehab.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rehab.rules.Snapshot
import rehab.rules.SnapshotJson
import java.nio.file.Files

class CaptureCoordinatorTest {
    private val dir = Files.createTempDirectory("captures").toFile()
    private val coordinator = CaptureCoordinator(dir)
    private fun snap(at: Long) = Snapshot("com.instagram.android", "352.0", at, emptyList())

    @Test fun nothingSavedWithoutRequest() {
        assertNull(coordinator.maybeSave(snap(1000)))
    }

    @Test fun savesFirstSnapshotAfterDelayThenClears() {
        coordinator.request(delayMillis = 5000, nowMillis = 1000)
        assertNull(coordinator.maybeSave(snap(3000)))
        val f = coordinator.maybeSave(snap(6000))
        assertNotNull(f)
        assertTrue(f!!.name.endsWith("_com.instagram.android.json"))
        assertEquals(6000L, SnapshotJson.decode(f.readText()).capturedAt)
        assertNull(coordinator.pendingAt.value)
        assertNull(coordinator.maybeSave(snap(7000)))
        assertEquals(1, coordinator.list().size)
    }
}
