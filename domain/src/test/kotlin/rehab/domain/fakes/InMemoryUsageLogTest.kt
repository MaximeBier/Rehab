package rehab.domain.fakes

import rehab.domain.model.TargetId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryUsageLogTest {
    private val t0 = Instant.parse("2026-09-21T10:00:00Z")
    private val target = TargetId("X")

    @Test fun `open cree un intervalle ouvert avec id`() {
        val log = InMemoryUsageLog()
        val i = log.open(target, t0)
        assertTrue(i.id > 0)
        assertTrue(i.open)
        assertEquals(i, log.openInterval())
    }

    @Test fun `update ferme et intervalsSince filtre`() {
        val log = InMemoryUsageLog()
        val i = log.open(target, t0)
        log.update(i.copy(end = t0.plusSeconds(60), open = false))
        assertNull(log.openInterval())
        assertEquals(1, log.intervalsSince(t0.plusSeconds(30)).size)
        assertEquals(0, log.intervalsSince(t0.plusSeconds(61)).size)
    }

    @Test fun `purgeBefore ne touche pas l intervalle ouvert`() {
        val log = InMemoryUsageLog()
        val a = log.open(target, t0)
        log.update(a.copy(end = t0.plusSeconds(10), open = false))
        log.open(target, t0.plusSeconds(20))
        log.purgeBefore(t0.plusSeconds(100))
        assertEquals(1, log.intervalsSince(Instant.EPOCH).size)
        assertTrue(log.intervalsSince(Instant.EPOCH).single().open)
    }
}
