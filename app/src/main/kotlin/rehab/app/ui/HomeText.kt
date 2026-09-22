package rehab.app.ui

import rehab.app.AppDisplayNames
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.policy.ActiveUnlock
import rehab.domain.policy.Schedule
import rehab.domain.policy.SlidingQuota
import rehab.domain.policy.StreakSummary
import rehab.rules.catalog.InstagramRules
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class PillTone { Accent, Muted, Warn }
data class Pill(val text: String, val tone: PillTone)
enum class AlertAction { OpenAccessibility, OpenDebug }
data class HomeAlert(val text: String, val action: AlertAction?)
data class Gauge(val label: String, val value: String, val fraction: Float, val exceeded: Boolean)
data class NightLine(val label: String, val value: String)

/** Textes de l'écran Accueil, en français. Toute la logique temporelle reçoit son horaire en paramètre. */
object HomeText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private fun Instant.hm(zone: ZoneId) = atZone(zone).format(hm)

    /**
     * Accord singulier/pluriel français : 0 et 1 prennent le singulier ("0 jour", "1 jour"),
     * 2 et plus le pluriel ("2 jours").
     */
    fun plural(n: Int, singular: String, plural: String = "${singular}s"): String =
        "$n " + if (n <= 1) singular else plural

    fun duration(d: Duration): String {
        val h = d.toHours()
        val m = d.toMinutes() % 60
        return when {
            h > 0 && m > 0 -> "$h h ${m.toString().padStart(2, '0')} min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }

    /** Minutes restantes arrondies au-dessus (au moins 1), formatées par [duration]. */
    fun waitText(from: Instant, to: Instant): String {
        val secs = Duration.between(from, to).seconds.coerceAtLeast(1)
        return duration(Duration.ofMinutes((secs + 59) / 60))
    }

    fun pill(serviceConnected: Boolean, decision: Decision, unlock: ActiveUnlock?, zone: ZoneId): Pill = when {
        !serviceConnected -> Pill("Service inactif", PillTone.Warn)
        unlock != null -> Pill("${kindName(unlock.kind)} · jusqu'à ${unlock.until.hm(zone)}", PillTone.Accent)
        decision is Decision.Block -> Pill(if (decision.reason == BlockReason.Night) "Bloqué · nuit" else "Bloqué · quota", PillTone.Muted)
        else -> Pill("Libre", PillTone.Accent)
    }

    private fun kindName(k: ActiveUnlock.Kind) = if (k == ActiveUnlock.Kind.Joker) "Joker" else "Relapse"

    fun statusLine(decision: Decision, unlock: ActiveUnlock?, now: Instant, zone: ZoneId): String? = when {
        unlock != null -> (if (unlock.kind == ActiveUnlock.Kind.Joker) "Joker actif" else "Relapse") + " · blocage suspendu jusqu'à ${unlock.until.hm(zone)}"
        decision is Decision.Block && decision.reason == BlockReason.Night -> "Nuit · déblocage à ${decision.unlockAt.hm(zone)}"
        decision is Decision.Block -> "Quota atteint · déblocage dans ${waitText(now, decision.unlockAt)} (${decision.unlockAt.hm(zone)})"
        else -> null
    }

    fun recordLine(s: StreakSummary): String? =
        if (s.inRecord) "Record en cours depuis " + plural(s.current - s.previousBest, "jour") else null

    fun metaLine(s: StreakSummary, jokersLeft: Int, jokersPerDay: Int): String =
        (if (s.inRecord) "ancien record ${s.previousBest}" else "record ${s.best}") + " · jokers $jokersLeft/$jokersPerDay"

    fun ringFraction(s: StreakSummary): Float = when {
        s.inRecord -> 1f
        s.best <= 0 -> 0f
        else -> (s.current.toFloat() / s.best).coerceIn(0f, 1f)
    }

    fun gauge(u: SlidingQuota.WindowUsage): Gauge = Gauge(
        label = "Fenêtre ${duration(u.window.duration)}",
        value = "${u.used.toMinutes()} / ${u.window.cap.toMinutes()} min",
        fraction = (u.used.seconds.toFloat() / u.window.cap.seconds).coerceIn(0f, 1f),
        exceeded = u.exceeded,
    )

    fun night(p: Schedule.NightPeriod?, now: Instant, zone: ZoneId): NightLine = when {
        p == null -> NightLine("Prochaine nuit", "aucune")
        p.start <= now -> NightLine("Nuit en cours", "→ ${p.end.hm(zone)}")
        else -> NightLine("Prochaine nuit", "${p.start.hm(zone)} → ${p.end.hm(zone)}")
    }

    fun serviceAlert() = HomeAlert("Service d'accessibilité désactivé — aucun blocage actif", AlertAction.OpenAccessibility)

    /**
     * Texte véridique (revue tâche 28) : pour Instagram, le mode dégradé « version » fait jouer la
     * règle `InstagramSuggested` (`degradedFallback`) sur tout l'onglet Accueil — il compte comme
     * cible (quota, nuit), il n'est pas bloqué en permanence. X n'a pas de repli.
     */
    fun outOfRangeAlert(packageName: String, version: String?): HomeAlert {
        val v = version?.split('.', '-')?.take(2)?.joinToString(".") ?: "?"
        val consequence = if (packageName == InstagramRules.PACKAGE) "tout l'onglet Accueil compte comme cible" else "la détection peut être incomplète"
        return HomeAlert("${AppDisplayNames.of(packageName)} $v hors plage testée — $consequence", AlertAction.OpenDebug)
    }

    fun notInstalledAlert(packageName: String) = HomeAlert("${AppDisplayNames.of(packageName)} n'est pas installée", null)

    fun unknownScreensAlert(packageName: String) = HomeAlert("${AppDisplayNames.of(packageName)} : écrans non reconnus — mode dégradé actif", AlertAction.OpenDebug)

    /** Unité affichée sous le chiffre de l'anneau : accord singulier/pluriel. */
    fun unit(n: Int) = if (n <= 1) "JOUR" else "JOURS"
}
