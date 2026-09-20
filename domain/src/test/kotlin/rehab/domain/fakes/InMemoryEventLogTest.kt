package rehab.domain.fakes

import rehab.domain.model.Event
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryEventLogTest {
    private val t0 = Instant.parse("2026-09-21T10:00:00Z")

    @Test fun `all trie par date et since filtre inclusivement`() {
        val log = InMemoryEventLog()
        log.append(Event.ServiceOn(t0.plusSeconds(20)))
        log.append(Event.ServiceOff(t0))
        log.append(Event.ServiceOn(t0.plusSeconds(10)))
        assertEquals(listOf(t0, t0.plusSeconds(10), t0.plusSeconds(20)), log.all().map { it.at })
        assertEquals(listOf(t0.plusSeconds(10), t0.plusSeconds(20)), log.since(t0.plusSeconds(10)).map { it.at })
    }
}
