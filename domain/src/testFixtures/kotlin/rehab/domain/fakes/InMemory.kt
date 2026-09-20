package rehab.domain.fakes

// Déplacées en `java-test-fixtures` (revue finale, mineur) : voir la note sur FakeClock
// (rehab.domain.time) — ces fakes vivaient dans src/main et partaient dans l'APK release,
// qui n'est pas minifié.

import rehab.domain.model.Event
import rehab.domain.model.Settings
import rehab.domain.model.TargetId
import rehab.domain.model.UsageInterval
import rehab.domain.ports.EventLog
import rehab.domain.ports.SettingsRepo
import rehab.domain.ports.StreakRecordRepo
import rehab.domain.ports.UsageLog
import java.time.Instant

class InMemoryUsageLog : UsageLog {
    private val items = linkedMapOf<Long, UsageInterval>()
    private var nextId = 1L

    override fun intervalsSince(from: Instant) = items.values.filter { it.open || it.end >= from }.sortedBy { it.start }
    override fun openInterval() = items.values.firstOrNull { it.open }
    override fun open(target: TargetId, start: Instant): UsageInterval {
        val i = UsageInterval(nextId++, target, start, start, open = true)
        items[i.id] = i
        return i
    }
    override fun update(interval: UsageInterval) { items[interval.id] = interval }
    override fun delete(id: Long) { items.remove(id) }
    override fun purgeBefore(instant: Instant) { items.values.removeIf { !it.open && it.end < instant } }
}

class InMemoryEventLog : EventLog {
    private val items = mutableListOf<Event>()
    override fun append(event: Event) { items += event }
    override fun all() = items.sortedBy { it.at }
    override fun since(from: Instant) = all().filter { it.at >= from }
}

class InMemorySettingsRepo(initial: Settings = Settings.DEFAULT) : SettingsRepo {
    private var value = initial
    override fun get() = value
    override fun set(settings: Settings) { value = settings }
}

class InMemoryStreakRecordRepo(private val installedAt: Instant) : StreakRecordRepo {
    private var best = 0
    override fun bestDays() = best
    override fun setBestDays(days: Int) { best = days }
    override fun installedAt() = installedAt
}
