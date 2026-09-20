package rehab.app.time

import rehab.domain.time.Clock
import java.time.Instant
import java.time.ZoneId

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
