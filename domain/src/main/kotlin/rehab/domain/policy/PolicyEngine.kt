package rehab.domain.policy

import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.ports.SettingsRepo
import rehab.domain.ports.UsageLog
import java.time.Instant

class PolicyEngine(
    private val settings: SettingsRepo,
    private val schedule: Schedule,
    private val quota: SlidingQuota,
    private val unlock: UnlockPolicy,
    private val usage: UsageLog,
) {
    fun evaluate(now: Instant): Decision {
        if (unlock.activeUnlockUntil(now) != null) return Decision.Allow
        schedule.activeNight(now)?.let { return Decision.Block(BlockReason.Night, it.end) }
        quotaStatus(now).unlockAt?.let { return Decision.Block(BlockReason.Quota, it) }
        return Decision.Allow
    }

    fun quotaStatus(now: Instant): SlidingQuota.Result {
        val windows = settings.get().quotaWindows
        val longest = windows.maxOfOrNull { it.duration } ?: return SlidingQuota.Result(emptyList(), null)
        return quota.evaluate(windows, usage.intervalsSince(now.minus(longest)), now)
    }
}
