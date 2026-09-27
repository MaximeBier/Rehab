package rehab.domain.policy

import rehab.domain.model.Settings
import rehab.domain.ports.SettingsRepo
import java.time.Instant

sealed interface GuardResult {
    data object Accepted : GuardResult
    data class Rejected(val reason: String, val unlockAt: Instant) : GuardResult
}

/**
 * Verrous d'édition. [settings] est le même SettingsRepo que celui que [schedule] et [engine] lisent :
 * une seule source de vérité pour les réglages courants.
 */
class SettingsGuard(
    private val settings: SettingsRepo,
    private val schedule: Schedule,
    private val engine: PolicyEngine,
) {

    fun validate(proposed: Settings, now: Instant): GuardResult {
        checkNight(proposed, now)?.let { return it }
        checkQuota(proposed, now)?.let { return it }
        checkUnlock(proposed, now)?.let { return it }
        return GuardResult.Accepted
    }

    /**
     * Pendant un blocage (nuit active ou quota dépassé), les réglages de l'appui long ne peuvent que se
     * durcir : plus de jokers, un appui plus court ou un déblocage plus long lèveraient le blocage en cours
     * sans relapse. Les durcir reste permis, comme pour les nuits et les quotas.
     */
    private fun checkUnlock(proposed: Settings, now: Instant): GuardResult? {
        val unlockAt = listOfNotNull(schedule.activeNight(now)?.end, engine.quotaStatus(now).unlockAt).maxOrNull() ?: return null
        val current = settings.get()
        val loosened = proposed.jokersPerDay > current.jokersPerDay ||
            proposed.jokerDuration > current.jokerDuration ||
            proposed.relapseDuration > current.relapseDuration ||
            proposed.holdDuration < current.holdDuration
        return if (loosened) GuardResult.Rejected("Blocage en cours", unlockAt) else null
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
