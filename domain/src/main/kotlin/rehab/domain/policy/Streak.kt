package rehab.domain.policy

import rehab.domain.model.Event
import rehab.domain.ports.EventLog
import rehab.domain.ports.StreakRecordRepo
import java.time.Instant

/**
 * [previousBest] : plus longue série passée **terminée** par un relapse. [best] : record persisté
 * (≥ [current] après appel). « Record en cours » = la série actuelle dépasse strictement toutes les
 * séries passées et égale le record persisté ; une égalité avec une série passée n'est pas un record.
 */
data class StreakSummary(val current: Int, val best: Int, val previousBest: Int) {
    val inRecord: Boolean get() = current > 0 && current > previousBest && current >= best
}

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

    /** Série actuelle, record persisté et plus longue série passée, à [now]. */
    fun summary(now: Instant): StreakSummary {
        val relapseDays = events.all().filterIsInstance<Event.Relapse>().map { schedule.dayOf(it.at) }.toSet()
        val today = schedule.dayOf(now)
        var day = schedule.dayOf(record.installedAt())
        var run = 0
        var previousBest = 0
        while (day <= today) {
            if (day in relapseDays) {
                previousBest = maxOf(previousBest, run)
                run = 0
            } else {
                run++
            }
            day = day.plusDays(1)
        }
        // Persiste la plus longue série connue (série passée ou en cours) : une série passée plus longue
        // que le record enregistré doit aussi devenir le nouveau record, même si `recordAndGetBest`
        // n'a jamais été appelé pendant qu'elle courait.
        return StreakSummary(current = run, best = persistBest(maxOf(run, previousBest)), previousBest = previousBest)
    }

    fun recordAndGetBest(now: Instant): Int = persistBest(current(now))

    /**
     * Persiste [c] comme nouveau record si supérieur au record actuel, et retourne le record résultant.
     * Appelée à la fois depuis le thread "rehab-engine" (`RehabAccessibilityService`) et depuis
     * `Dispatchers.IO` (`RehabViewModel.compute()`). `@Synchronized` sérialise ces deux appelants : sans ça,
     * deux threads pouvaient lire le même `bestDays()` avant que l'un des deux n'écrive, perdant la mise à
     * jour de l'autre (write clobbering classique d'un read-modify-write non protégé).
     */
    @Synchronized
    private fun persistBest(c: Int): Int {
        val b = record.bestDays()
        if (c > b) {
            record.setBestDays(c)
            return c
        }
        return b
    }
}
