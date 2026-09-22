package rehab.app.ui

import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.model.Settings
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/**
 * Une fonction par édition possible depuis l'écran Réglages : chacune part des réglages
 * persistés et renvoie la version modifiée (ou un échec de validation). Aucune ne touche au
 * disque — c'est `RehabViewModel.saveSettings` qui valide (`SettingsGuard`) puis persiste.
 */
object SettingsEdits {
    fun withNight(s: Settings, day: DayOfWeek, bedtime: LocalTime, wakeup: LocalTime): Settings =
        s.copy(nights = s.nights + (day to NightWindow(bedtime, wakeup)))

    fun withWindow(s: Settings, index: Int, durationText: String, capText: String): Result<Settings> =
        window(durationText, capText).map { w -> s.copy(quotaWindows = s.quotaWindows.toMutableList().also { it[index] = w }) }

    fun addWindow(s: Settings, durationText: String, capText: String): Result<Settings> =
        window(durationText, capText).map { s.copy(quotaWindows = s.quotaWindows + it) }

    fun removeWindow(s: Settings, index: Int): Settings =
        s.copy(quotaWindows = s.quotaWindows.filterIndexed { i, _ -> i != index })

    fun withJokerMinutes(s: Settings, text: String) = runCatching { s.copy(jokerDuration = Duration.ofMinutes(parsePositiveLong(text, "Durée d’un joker"))) }
    fun withRelapseMinutes(s: Settings, text: String) = runCatching { s.copy(relapseDuration = Duration.ofMinutes(parsePositiveLong(text, "Durée d’un relapse"))) }
    fun withJokersPerDay(s: Settings, text: String) = runCatching { s.copy(jokersPerDay = parseNonNegativeInt(text, "Jokers par jour")) }
    fun withHoldSeconds(s: Settings, text: String) = runCatching { s.copy(holdDuration = Duration.ofSeconds(parsePositiveLong(text, "Durée d’appui"))) }

    private fun window(durationText: String, capText: String): Result<QuotaWindow> = runCatching {
        val d = parsePositiveLong(durationText, "Durée")
        val c = parsePositiveLong(capText, "Plafond")
        if (c > d) error("Le plafond dépasse la durée de la fenêtre")
        QuotaWindow(Duration.ofMinutes(d), Duration.ofMinutes(c))
    }

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
