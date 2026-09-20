package rehab.app.ui

import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.policy.SlidingQuota
import rehab.rules.catalog.InstagramRules
import rehab.rules.catalog.TwitterRules
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Textes de l'écran Accueil, en français. Toute la logique temporelle reçoit son horaire en paramètre. */
object HomeText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Accord singulier/pluriel français : 0 et 1 prennent le singulier ("0 jour", "1 jour"),
     * 2 et plus le pluriel ("2 jours").
     */
    fun plural(n: Int, singular: String, plural: String = "${singular}s"): String =
        "$n " + if (n <= 1) singular else plural

    fun outOfRangeMessage(packageName: String, version: String?): String {
        val app = when (packageName) {
            InstagramRules.PACKAGE -> "Instagram"
            TwitterRules.PACKAGE -> "X"
            else -> packageName
        }
        return "$app $version hors plage testée : $app est bloqué en entier en attendant une mise à jour des règles."
    }

    fun duration(d: Duration): String {
        val h = d.toHours()
        val m = d.toMinutes() % 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }

    fun quotaLine(u: SlidingQuota.WindowUsage): String =
        "${u.used.toMinutes()} / ${u.window.cap.toMinutes()} min sur ${duration(u.window.duration)}"

    fun status(decision: Decision, unlockUntil: Instant?, zone: ZoneId): String {
        if (unlockUntil != null) return "Déblocage en cours jusqu'à " + unlockUntil.atZone(zone).format(hm)
        return when (decision) {
            Decision.Allow -> "Libre"
            is Decision.Block -> {
                val why = if (decision.reason == BlockReason.Night) "nuit" else "quota"
                "Bloqué ($why) jusqu'à " + decision.unlockAt.atZone(zone).format(hm)
            }
        }
    }
}
