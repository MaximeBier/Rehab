package rehab.domain.time

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeClockTest {
    @Test fun `advance avance l heure`() {
        val c = FakeClock(Instant.parse("2026-09-19T10:00:00Z"))
        c.advance(Duration.ofMinutes(5))
        assertEquals(Instant.parse("2026-09-19T10:05:00Z"), c.now())
    }
}
