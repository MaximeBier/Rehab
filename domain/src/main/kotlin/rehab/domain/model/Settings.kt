package rehab.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** Ligne du jour J : coucher le soir de J (ou après minuit), lever le matin de J+1. */
data class NightWindow(val bedtime: LocalTime, val wakeup: LocalTime) {
    val isEmpty: Boolean get() = bedtime == wakeup
}

data class QuotaWindow(val duration: Duration, val cap: Duration) {
    init {
        require(!duration.isNegative && !duration.isZero) { "durée > 0 requise" }
        require(!cap.isNegative && !cap.isZero) { "plafond > 0 requis" }
        require(cap <= duration) { "plafond ≤ durée requis" }
    }
}

data class Settings(
    val nights: Map<DayOfWeek, NightWindow>,
    val quotaWindows: List<QuotaWindow>,
    val jokerDuration: Duration,
    val relapseDuration: Duration,
    val jokersPerDay: Int,
    val holdDuration: Duration,
) {
    companion object {
        val DEFAULT = Settings(
            nights = DayOfWeek.entries.associateWith { day ->
                if (day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY)
                    NightWindow(LocalTime.of(0, 30), LocalTime.of(9, 0))
                else
                    NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30))
            },
            quotaWindows = listOf(
                QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5)),
                QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30)),
            ),
            jokerDuration = Duration.ofMinutes(5),
            relapseDuration = Duration.ofMinutes(15),
            jokersPerDay = 2,
            holdDuration = Duration.ofSeconds(10),
        )
    }
}
