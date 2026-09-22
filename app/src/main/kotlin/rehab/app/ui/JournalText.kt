package rehab.app.ui

import rehab.domain.model.BlockReason
import rehab.domain.model.Event
import rehab.domain.model.UsageInterval
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class JournalLine(val atMillis: Long, val text: String)

object JournalText {
    private val fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    private fun stamp(at: Instant, zone: ZoneId) = at.atZone(zone).format(fmt)

    fun line(event: Event, zone: ZoneId): String = stamp(event.at, zone) + " · " + when (event) {
        is Event.Joker -> "Joker (+${Duration.between(event.at, event.unlockUntil).toMinutes()} min)"
        is Event.Relapse -> "RELAPSE (+${Duration.between(event.at, event.unlockUntil).toMinutes()} min)"
        is Event.ServiceOn -> "Service activé"
        is Event.ServiceOff -> "Service désactivé"
        is Event.RulesOutOfRange -> "Règles hors plage : ${event.packageName} ${event.version}"
        is Event.Error -> "Erreur : ${event.message}"
        // Branche provisoire : réécrite en tâche 5.
        is Event.Block -> "Blocage ${if (event.reason == BlockReason.Night) "nuit" else "quota"}"
    }

    fun line(interval: UsageInterval, zone: ZoneId): String {
        val d = Duration.between(interval.start, interval.end)
        return stamp(interval.start, zone) + " · ${interval.target.value} · ${d.toMinutes()} min ${d.seconds % 60} s" + if (interval.open) " (en cours)" else ""
    }
}
