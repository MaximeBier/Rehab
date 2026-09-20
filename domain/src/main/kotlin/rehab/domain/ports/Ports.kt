package rehab.domain.ports

import rehab.domain.model.Event
import rehab.domain.model.Settings
import rehab.domain.model.TargetId
import rehab.domain.model.UsageInterval
import java.time.Instant

interface UsageLog {
    fun intervalsSince(from: Instant): List<UsageInterval>
    fun openInterval(): UsageInterval?
    fun open(target: TargetId, start: Instant): UsageInterval
    fun update(interval: UsageInterval)
    fun delete(id: Long)
    fun purgeBefore(instant: Instant)
}

interface EventLog {
    fun append(event: Event)
    fun all(): List<Event>
}

interface SettingsRepo {
    fun get(): Settings
    fun set(settings: Settings)
}

interface StreakRecordRepo {
    fun bestDays(): Int
    fun setBestDays(days: Int)
    fun installedAt(): Instant
}
