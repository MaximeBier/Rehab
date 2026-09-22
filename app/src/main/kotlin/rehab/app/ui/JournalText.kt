package rehab.app.ui

import rehab.app.AppDisplayNames
import rehab.domain.model.BlockReason
import rehab.domain.model.Event
import rehab.domain.model.UsageInterval
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Couleur d'accroche d'une ligne du Journal (point + libellé), DESIGN.md §2. */
enum class JournalTone { Danger, Accent, Warn, Neutral }

data class JournalRow(
    val atMillis: Long,
    val time: String,
    val label: String,
    val type: String,
    val detail: String,
    val tone: JournalTone,
)

data class JournalDay(val header: String, val rows: List<JournalRow>)

/**
 * Traduction d'événements/intervalles bruts en lignes de Journal, groupées par journée Rehab
 * (`dayOf`, décalée de l'heure de lever). Aucune dépendance à Room ni au ViewModel : la présentation
 * (`JournalScreen`) reçoit déjà des `JournalDay` construits à partir de ces fonctions pures.
 */
object JournalText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private fun time(at: Instant, zone: ZoneId) = at.atZone(zone).format(hm)
    private fun minutes(from: Instant, to: Instant) = Duration.between(from, to).toMinutes()

    fun rows(
        events: List<Event>,
        usage: List<UsageInterval>,
        jokersPerDay: Int,
        dayOf: (Instant) -> LocalDate,
        now: Instant,
        zone: ZoneId,
    ): List<JournalRow> {
        // Rang 1-based de chaque joker parmi les jokers de sa journée Rehab, trié par heure croissante
        // (le plus ancien du jour = rang 1) : sert à afficher le nombre de jokers restants ce jour-là.
        val jokerRank: Map<Event.Joker, Int> = events.filterIsInstance<Event.Joker>()
            .groupBy { dayOf(it.at) }
            .flatMap { (_, jokersOfDay) -> jokersOfDay.sortedBy { it.at }.mapIndexed { index, joker -> joker to (index + 1) } }
            .toMap()

        val out = mutableListOf<JournalRow>()

        events.forEach { e ->
            when (e) {
                is Event.Joker -> {
                    val rang = jokerRank.getValue(e)
                    val restant = (jokersPerDay - rang).coerceAtLeast(0)
                    out += JournalRow(
                        e.at.toEpochMilli(), time(e.at, zone),
                        "Joker · déblocage ${minutes(e.at, e.unlockUntil)} min", "Événement",
                        HomeText.plural(restant, "restant"), JournalTone.Accent,
                    )
                }
                is Event.Relapse -> out += JournalRow(
                    e.at.toEpochMilli(), time(e.at, zone),
                    "Relapse · déblocage ${minutes(e.at, e.unlockUntil)} min", "Événement",
                    "${time(e.at, zone)} → ${time(e.unlockUntil, zone)}", JournalTone.Danger,
                )
                is Event.Block -> {
                    val reasonLabel = if (e.reason == BlockReason.Night) "nuit" else "quota"
                    val reasonType = if (e.reason == BlockReason.Night) "Nuit" else "Quota"
                    val type = "Blocage · $reasonType"
                    out += JournalRow(e.at.toEpochMilli(), time(e.at, zone), "Blocage $reasonLabel", type, "→ ${time(e.until, zone)}", JournalTone.Neutral)
                    if (e.until <= now) {
                        out += JournalRow(e.until.toEpochMilli(), time(e.until, zone), "Fin du blocage $reasonLabel", type, "—", JournalTone.Neutral)
                    }
                }
                is Event.ServiceOn -> {
                    val reactivated = events.any { it is Event.ServiceOff && it.at < e.at }
                    out += JournalRow(
                        e.at.toEpochMilli(), time(e.at, zone),
                        if (reactivated) "Service réactivé" else "Service activé", "Service", "—", JournalTone.Neutral,
                    )
                }
                is Event.ServiceOff -> out += JournalRow(e.at.toEpochMilli(), time(e.at, zone), "Service désactivé", "Service", "—", JournalTone.Warn)
                is Event.RulesOutOfRange -> {
                    val version = e.version.split('.', '-').take(2).joinToString(".")
                    out += JournalRow(
                        e.at.toEpochMilli(), time(e.at, zone),
                        "Règles hors plage · ${AppDisplayNames.of(e.packageName)} $version", "Règles", "notifié", JournalTone.Warn,
                    )
                }
                is Event.Error -> out += JournalRow(e.at.toEpochMilli(), time(e.at, zone), "Erreur · ${e.message.take(80)}", "Erreur", "—", JournalTone.Warn)
            }
        }

        usage.forEach { u ->
            val detail = when {
                u.open -> "en cours"
                else -> {
                    val d = Duration.between(u.start, u.end)
                    if (d.seconds < 60) "${d.seconds} s" else "%d min %02d s".format(d.toMinutes(), d.seconds % 60)
                }
            }
            out += JournalRow(u.start.toEpochMilli(), time(u.start, zone), AppDisplayNames.target(u.target.value), "Usage", detail, JournalTone.Neutral)
        }

        return out.sortedByDescending { it.atMillis }
    }

    /** Regroupe des lignes déjà triées par journée Rehab, jour le plus récent en premier. */
    fun days(rows: List<JournalRow>, dayOf: (Instant) -> LocalDate, today: LocalDate): List<JournalDay> =
        rows.groupBy { dayOf(Instant.ofEpochMilli(it.atMillis)) }
            .toList()
            .sortedByDescending { (day, _) -> day }
            .map { (day, dayRows) -> JournalDay(dayHeader(day, today), dayRows) }

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

    fun dayHeader(day: LocalDate, today: LocalDate): String {
        val fmt = day.format(dayFmt)
        return when (day) {
            today -> "Aujourd'hui · $fmt"
            today.minusDays(1) -> "Hier · $fmt"
            else -> fmt
        }
    }
}
