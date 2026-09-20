package rehab.domain.policy

import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.Settings
import java.time.Instant
import java.time.ZoneId

sealed interface GuardResult {
    data object Accepted : GuardResult
    data class Rejected(val reason: String, val unlockAt: Instant) : GuardResult
}

class SettingsGuard(private val zone: ZoneId, private val engine: PolicyEngine) {

    fun validate(current: Settings, proposed: Settings, now: Instant): GuardResult {
        checkNight(current, proposed, now)?.let { return it }
        checkQuota(proposed, now)?.let { return it }
        return GuardResult.Accepted
    }

    private fun checkNight(current: Settings, proposed: Settings, now: Instant): GuardResult? {
        val active = Schedule({ current.nights }, zone).activeNight(now) ?: return null
        val proposedPeriod = Schedule({ proposed.nights }, zone).periodForRow(active.row)
        val ok = proposedPeriod != null && proposedPeriod.start <= active.start && proposedPeriod.end >= active.end
        return if (ok) null else GuardResult.Rejected("Plage nocturne en cours", active.end)
    }

    private fun checkQuota(proposed: Settings, now: Instant): GuardResult? {
        val decision = engine.evaluate(now) as? Decision.Block ?: return null
        if (decision.reason != BlockReason.Quota) return null
        val exceeded = engine.quotaStatus(now).perWindow.filter { it.exceeded }.map { it.window }
        val ok = exceeded.all { w -> proposed.quotaWindows.any { it.duration == w.duration && it.cap <= w.cap } }
        return if (ok) null else GuardResult.Rejected("Quota en cours", decision.unlockAt)
    }
}
