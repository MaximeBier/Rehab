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

    /**
     * Retourne le meilleur streak, et **persiste** un nouveau record si [current] le dépasse. Le nom
     * précédent (`best`) suggérait une simple lecture ; or c'est une lecture-modification-écriture sur
     * [record], appelée à la fois depuis le thread "rehab-engine" (`RehabAccessibilityService`) et depuis
     * `Dispatchers.IO` (`RehabViewModel.compute()`). `@Synchronized` sérialise ces deux appelants : sans ça,
     * deux threads pouvaient lire le même `bestDays()` avant que l'un des deux n'écrive, perdant la mise à
     * jour de l'autre (write clobbering classique d'un read-modify-write non protégé).
     */
    @Synchronized
    fun recordAndGetBest(now: Instant): Int {
        val c = current(now)
        val b = record.bestDays()
        if (c > b) {
            record.setBestDays(c)
            return c
        }
        return b
    }
}
