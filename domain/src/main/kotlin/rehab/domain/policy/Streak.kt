package rehab.domain.policy

import rehab.domain.model.Event
import rehab.domain.ports.EventLog
import rehab.domain.ports.StreakRecordRepo
import java.time.Instant

class Streak(
    private val schedule: Schedule,
    private val events: EventLog,
    private val record: StreakRecordRepo,
) {
    fun current(now: Instant): Int {
        val relapseDays = events.all().filterIsInstance<Event.Relapse>().map { schedule.dayOf(it.at) }.toSet()
        val firstDay = schedule.dayOf(record.installedAt())
        var day = schedule.dayOf(now)
        var count = 0
        while (day >= firstDay && day !in relapseDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun best(now: Instant): Int {
        val c = current(now)
        val b = record.bestDays()
        if (c > b) {
            record.setBestDays(c)
            return c
        }
        return b
    }
}
