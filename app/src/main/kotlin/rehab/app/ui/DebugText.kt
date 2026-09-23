package rehab.app.ui

import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.policy.ActiveUnlock
import rehab.domain.policy.SlidingQuota
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** Textes de l'écran Debug, en français. Toute la logique temporelle reçoit son horaire en paramètre. */
object DebugText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private fun Instant.hm(zone: ZoneId) = atZone(zone).format(hm)

    /**
     * Résumé une ligne de la décision courante : le déblocage actif (joker/relapse) prime sur le
     * blocage lui-même, qui prime sur la fenêtre de quota la plus chargée (used/cap le plus proche
     * de la limite) affichée à titre indicatif quand la décision est `Allow`.
     */
    fun decision(decision: Decision, unlock: ActiveUnlock?, usages: List<SlidingQuota.WindowUsage>, zone: ZoneId): String {
        val base = if (decision is Decision.Allow) "Allow" else "Block"
        val suffix = when {
            unlock != null -> {
                val kind = if (unlock.kind == ActiveUnlock.Kind.Joker) "joker" else "relapse"
                "$kind → ${unlock.until.hm(zone)}"
            }
            decision is Decision.Block -> {
                val reason = if (decision.reason == BlockReason.Night) "nuit" else "quota"
                "$reason → ${decision.unlockAt.hm(zone)}"
            }
            usages.isNotEmpty() -> {
                val top = usages.maxBy { it.used.toMillis().toDouble() / it.window.cap.toMillis() }
                "quota ${top.used.toMinutes()}/${top.window.cap.toMinutes()} min"
            }
            else -> null
        }
        return if (suffix != null) "$base · $suffix" else base
    }

    /** « il y a » : secondes en dessous de la minute, minutes:secondes en dessous de l'heure, puis heures:minutes. */
    fun ago(nowMillis: Long, atMillis: Long): String {
        val totalSeconds = ((nowMillis - atMillis).coerceAtLeast(0)) / 1000
        return when {
            totalSeconds < 60 -> "$totalSeconds s"
            totalSeconds < 3600 -> {
                val m = totalSeconds / 60
                val s = totalSeconds % 60
                "$m min ${s.toString().padStart(2, '0')} s"
            }
            else -> {
                val h = totalSeconds / 3600
                val m = (totalSeconds % 3600) / 60
                "$h h ${m.toString().padStart(2, '0')} min"
            }
        }
    }

    /** Taille lisible : Ko arrondis au-dessus (au moins 1) sous 1 Mo, sinon Mo à une décimale (virgule). */
    fun size(bytes: Long): String {
        if (bytes < 1024L * 1024L) {
            val ko = ((bytes + 1023) / 1024).coerceAtLeast(1)
            return "$ko Ko"
        }
        val mo = (bytes.toDouble() / (1024.0 * 1024.0) * 10.0).roundToLong() / 10.0
        return "${String.format(Locale.ROOT, "%.1f", mo).replace('.', ',')} Mo"
    }
}
