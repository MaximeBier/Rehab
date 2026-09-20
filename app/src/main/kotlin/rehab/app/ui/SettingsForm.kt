package rehab.app.ui

import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.model.Settings
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class SettingsForm(
    val nights: Map<DayOfWeek, Pair<String, String>>,
    val quotas: List<Pair<String, String>>,
    val jokerMinutes: String,
    val relapseMinutes: String,
    val jokersPerDay: String,
    val holdSeconds: String,
) {
    fun toSettings(): Result<Settings> = runCatching {
        Settings(
            nights = nights.mapValues { (day, pair) ->
                val name = dayName(day)
                NightWindow(parseTime(pair.first, "$name, coucher"), parseTime(pair.second, "$name, lever"))
            },
            quotaWindows = quotas.mapIndexed { i, (dur, cap) ->
                val d = parsePositiveLong(dur, "Fenêtre ${i + 1} : durée")
                val c = parsePositiveLong(cap, "Fenêtre ${i + 1} : plafond")
                if (c > d) error("Fenêtre ${i + 1} : le plafond dépasse la durée")
                QuotaWindow(Duration.ofMinutes(d), Duration.ofMinutes(c))
            },
            jokerDuration = Duration.ofMinutes(parsePositiveLong(jokerMinutes, "Durée du joker")),
            relapseDuration = Duration.ofMinutes(parsePositiveLong(relapseMinutes, "Durée du relapse")),
            jokersPerDay = parseNonNegativeInt(jokersPerDay, "Nombre de jokers"),
            holdDuration = Duration.ofSeconds(parsePositiveLong(holdSeconds, "Durée d'appui")),
        )
    }

    companion object {
        private val hm = DateTimeFormatter.ofPattern("HH:mm")

        fun from(s: Settings) = SettingsForm(
            nights = DayOfWeek.entries.associateWith { d ->
                val w = s.nights[d] ?: NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30))
                w.bedtime.format(hm) to w.wakeup.format(hm)
            },
            quotas = s.quotaWindows.map { it.duration.toMinutes().toString() to it.cap.toMinutes().toString() },
            jokerMinutes = s.jokerDuration.toMinutes().toString(),
            relapseMinutes = s.relapseDuration.toMinutes().toString(),
            jokersPerDay = s.jokersPerDay.toString(),
            holdSeconds = s.holdDuration.seconds.toString(),
        )

        fun dayName(d: DayOfWeek): String =
            d.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }

        /**
         * [quotas] respecte la convention (durée, plafond). Ces deux fonctions sont le seul point
         * d'écriture d'une ligne : elles évitent qu'un appel d'UI construise la paire dans le
         * mauvais ordre et permute silencieusement durée et plafond.
         */
        fun withQuotaCap(quotas: List<Pair<String, String>>, index: Int, cap: String): List<Pair<String, String>> =
            quotas.toMutableList().also { it[index] = it[index].first to cap }

        fun withQuotaDuration(quotas: List<Pair<String, String>>, index: Int, duration: String): List<Pair<String, String>> =
            quotas.toMutableList().also { it[index] = duration to it[index].second }

        private fun parseTime(text: String, label: String): LocalTime =
            runCatching { LocalTime.parse(text.trim(), hm) }.getOrElse { error("$label : heure invalide (attendu HH:mm)") }

        /** Champs numériques en minutes ou secondes : rejette vide, non numérique et ≤ 0 avec un message ciblé. */
        private fun parsePositiveLong(text: String, label: String): Long {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) error("$label : valeur manquante")
            val v = trimmed.toLongOrNull() ?: error("$label : nombre entier attendu")
            if (v <= 0) error("$label : doit être supérieur à 0")
            return v
        }

        private fun parseNonNegativeInt(text: String, label: String): Int {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) error("$label : valeur manquante")
            val v = trimmed.toIntOrNull() ?: error("$label : nombre entier attendu")
            if (v < 0) error("$label : ne peut pas être négatif")
            return v
        }
    }
}
