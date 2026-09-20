package rehab.app.ui

import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.policy.SlidingQuota
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Textes de l'écran Accueil, en français. Toute la logique temporelle reçoit son horaire en paramètre. */
object HomeText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

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
