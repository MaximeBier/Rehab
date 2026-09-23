package rehab.app.ui

import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.GuardResult
import rehab.rules.RedirectTab
import rehab.rules.catalog.InstagramRules
import rehab.rules.catalog.TwitterRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Textes de l'écran Réglages, en français. Toute la logique temporelle reçoit son horaire en paramètre. */
object SettingsText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    fun nightValue(w: NightWindow) = if (w.isEmpty) "désactivée" else "${w.bedtime.format(hm)} → ${w.wakeup.format(hm)}"
    fun windowLabel(w: QuotaWindow) = "Sur ${HomeText.duration(w.duration)}"
    fun windowValue(w: QuotaWindow) = "${HomeText.duration(w.cap)} max"
    fun minutes(d: java.time.Duration) = "${d.toMinutes()} min"
    fun seconds(d: java.time.Duration) = "${d.seconds} s"

    fun dayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }

    fun nightLockReason(end: Instant, zone: ZoneId) = "Nuit en cours — modifiable à partir de ${end.atZone(zone).format(hm)}"
    fun quotaLockReason(unlockAt: Instant, now: Instant) = "Quota dépassé — modifiable dans ${HomeText.waitText(now, unlockAt)}"
    fun rejection(r: GuardResult.Rejected, zone: ZoneId) = "${r.reason} : modifiable à partir de ${r.unlockAt.atZone(zone).format(hm)}."

    /** Apps proposées dans « Bascule au blocage », dans l'ordre d'affichage (package → libellé). */
    val redirectApps: List<Pair<String, String>> = listOf(InstagramRules.PACKAGE to "Instagram", TwitterRules.PACKAGE to "X")

    /** Valeur d'une ligne « Bascule au blocage » : `null` = bascule désactivée (overlay comme avant). */
    fun redirectValue(tab: RedirectTab?) = when (tab) {
        RedirectTab.Messages -> "Messages"
        RedirectTab.Search -> "Recherche"
        null -> "Désactivée"
    }
}
