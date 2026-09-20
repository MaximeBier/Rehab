package rehab.domain.usage

import rehab.domain.model.TargetId
import rehab.domain.model.UsageInterval
import rehab.domain.ports.UsageLog
import java.time.Duration
import java.time.Instant

class UsageTracker(
    private val usage: UsageLog,
    private val minDuration: Duration = Duration.ofSeconds(2),
    private val persistEvery: Duration = Duration.ofSeconds(10),
) {
    private var lastPersist: Instant? = null

    fun recover() {
        usage.openInterval()?.let { finish(it.copy(open = false)) }
    }

    fun onDetected(target: TargetId?, now: Instant) {
        val open = usage.openInterval()
        if (target == null) {
            open?.let { close(it, now) }
            return
        }
        if (open == null || open.target != target) {
            open?.let { close(it, now) }
            usage.open(target, now)
            lastPersist = now
            return
        }
        val last = lastPersist
        if (last == null || Duration.between(last, now) >= persistEvery) {
            usage.update(open.copy(end = now))
            lastPersist = now
        }
    }

    fun closeOpen(now: Instant) {
        usage.openInterval()?.let { close(it, now) }
    }

    private fun close(interval: UsageInterval, now: Instant) = finish(interval.copy(end = now, open = false))

    private fun finish(closed: UsageInterval) {
        if (Duration.between(closed.start, closed.end) < minDuration) usage.delete(closed.id)
        else usage.update(closed)
        lastPersist = null
    }
}
