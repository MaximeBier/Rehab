package rehab.domain.policy

import rehab.domain.model.Settings
import java.time.Instant

sealed interface GuardResult {
    data object Accepted : GuardResult
    data class Rejected(val reason: String, val unlockAt: Instant) : GuardResult
}

/**
 * Verrous d'édition. Les réglages courants sont ceux que [schedule] et [engine] lisent déjà
 * (même SettingsRepo) : une seule source de vérité.
 */
class SettingsGuard(private val schedule: Schedule, private val engine: PolicyEngine) {

    fun validate(proposed: Settings, now: Instant): GuardResult {
        checkNight(proposed, now)?.let { return it }
        checkQuota(proposed, now)?.let { return it }
        return GuardResult.Accepted
    }

    private fun checkNight(proposed: Settings, now: Instant): GuardResult? {
        val active = schedule.activeNight(now) ?: return null
        val proposedPeriod = Schedule({ proposed.nights }, schedule.zone).periodForRow(active.row)
        val ok = proposedPeriod != null && proposedPeriod.start <= active.start && proposedPeriod.end >= active.end
        return if (ok) null else GuardResult.Rejected("Plage nocturne en cours", active.end)
    }

    /** Verrouillé tant qu'au moins une fenêtre est en dépassement, même si un joker, un relapse ou la nuit masque le quota. */
    private fun checkQuota(proposed: Settings, now: Instant): GuardResult? {
        val status = engine.quotaStatus(now)
        val unlockAt = status.unlockAt ?: return null
        val exceeded = status.perWindow.filter { it.exceeded }.map { it.window }
        val ok = exceeded.all { w -> proposed.quotaWindows.any { it.duration == w.duration && it.cap <= w.cap } }
        return if (ok) null else GuardResult.Rejected("Quota en cours", unlockAt)
    }
}
