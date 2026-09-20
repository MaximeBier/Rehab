package rehab.domain.time

import java.time.Instant
import java.time.ZoneId

interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
}
