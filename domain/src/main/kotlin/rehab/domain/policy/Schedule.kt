package rehab.domain.policy

import rehab.domain.model.NightWindow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class Schedule(
    private val nights: () -> Map<DayOfWeek, NightWindow>,
    val zone: ZoneId,
) {
    data class NightPeriod(val row: LocalDate, val start: Instant, val end: Instant)

    fun periodForRow(row: LocalDate): NightPeriod? {
        val w = nights()[row.dayOfWeek] ?: return null
        if (w.isEmpty) return null
        val next = row.plusDays(1)
        val end = next.atTime(w.wakeup).atZone(zone).toInstant()
        val startDate = if (w.bedtime > w.wakeup) row else next
        val start = startDate.atTime(w.bedtime).atZone(zone).toInstant()
        return NightPeriod(row, start, end)
    }

    fun activeNight(now: Instant): NightPeriod? {
        val today = now.atZone(zone).toLocalDate()
        return listOf(today.minusDays(1), today)
            .mapNotNull { periodForRow(it) }
            .firstOrNull { now >= it.start && now < it.end }
    }

    fun dayStart(date: LocalDate): Instant {
        val w = nights()[date.minusDays(1).dayOfWeek]
        val time = if (w == null || w.isEmpty) LocalTime.MIDNIGHT else w.wakeup
        return date.atTime(time).atZone(zone).toInstant()
    }

    fun dayOf(now: Instant): LocalDate {
        val d = now.atZone(zone).toLocalDate()
        return if (now >= dayStart(d)) d else d.minusDays(1)
    }
}
