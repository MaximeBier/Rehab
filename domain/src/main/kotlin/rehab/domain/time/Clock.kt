package rehab.domain.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
}

class FakeClock(start: Instant, private val zoneId: ZoneId = ZoneId.of("Europe/Paris")) : Clock {
    private var current: Instant = start
    override fun now(): Instant = current
    override fun zone(): ZoneId = zoneId
    fun advance(d: Duration) { current = current.plus(d) }
    fun set(instant: Instant) { current = instant }
}
