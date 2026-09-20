package rehab.domain.policy

import rehab.domain.model.Event
import rehab.domain.model.Settings
import rehab.domain.ports.EventLog
import rehab.domain.ports.SettingsRepo
import java.time.Instant
import java.time.LocalDate

sealed interface PressOutcome {
    data class Joker(val remainingAfter: Int) : PressOutcome
    data class Relapse(val streakLost: Int) : PressOutcome
}

class UnlockPolicy(
    private val settings: SettingsRepo,
    private val schedule: Schedule,
    private val events: EventLog,
    private val streak: Streak,
) {
    fun activeUnlockUntil(now: Instant): Instant? {
        val s = settings.get()
        val horizon = now.minus(maxOf(s.jokerDuration, s.relapseDuration))
        return events.since(horizon)
            .mapNotNull {
                when (it) {
                    is Event.Joker -> it.unlockUntil
                    is Event.Relapse -> it.unlockUntil
                    else -> null
                }
            }
            .filter { it > now }
            .maxOrNull()
    }

    fun jokersUsed(day: LocalDate): Int =
        events.since(schedule.dayStart(day)).filterIsInstance<Event.Joker>().count { schedule.dayOf(it.at) == day }

    fun preview(now: Instant): PressOutcome = outcome(now, settings.get())

    fun commit(now: Instant): Event {
        val s = settings.get()
        val event = when (outcome(now, s)) {
            is PressOutcome.Joker -> Event.Joker(now, now.plus(s.jokerDuration))
            is PressOutcome.Relapse -> Event.Relapse(now, now.plus(s.relapseDuration))
        }
        events.append(event)
        return event
    }

    private fun outcome(now: Instant, s: Settings): PressOutcome {
        val used = jokersUsed(schedule.dayOf(now))
        val isNight = schedule.activeNight(now) != null
        return if (!isNight && used < s.jokersPerDay) PressOutcome.Joker(s.jokersPerDay - used - 1)
        else PressOutcome.Relapse(streak.current(now))
    }
}
