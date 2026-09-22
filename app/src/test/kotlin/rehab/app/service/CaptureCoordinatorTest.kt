package rehab.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import rehab.rules.Snapshot
import rehab.rules.SnapshotJson
import java.nio.file.Files
import java.time.ZoneId

class CaptureCoordinatorTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val dir = Files.createTempDirectory("captures").toFile()
    private val coordinator = CaptureCoordinator(dir, zone)
    private fun snap(at: Long) = Snapshot("com.instagram.android", "352.0", at, emptyList())

    @Test fun nothingSavedWithoutRequest() {
        assertNull(coordinator.maybeSave(snap(1000), "instagram.reels"))
    }

    @Test fun savesFirstSnapshotAfterDelayThenClears() {
        coordinator.request(delayMillis = 5000, nowMillis = 1000)
        assertNull(coordinator.maybeSave(snap(3000), "instagram.reels"))
        val f = coordinator.maybeSave(snap(6000), "instagram.reels")
        assertNotNull(f)
        assertEquals(CaptureNames.fileName("com.instagram.android", "instagram.reels", 6000, zone), f!!.name)
        assertEquals(6000L, SnapshotJson.decode(f.readText()).capturedAt)
        assertNull(coordinator.pendingAt.value)
        assertNull(coordinator.maybeSave(snap(7000), "instagram.reels"))
        assertEquals(1, coordinator.list().size)
    }
}
