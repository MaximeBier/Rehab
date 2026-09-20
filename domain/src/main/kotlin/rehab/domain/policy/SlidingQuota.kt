package rehab.domain.policy

import rehab.domain.model.QuotaWindow
import rehab.domain.model.UsageInterval
import java.time.Duration
import java.time.Instant

class SlidingQuota {
    data class WindowUsage(val window: QuotaWindow, val used: Duration) {
        val exceeded: Boolean get() = used >= window.cap
    }

    data class Result(val perWindow: List<WindowUsage>, val unlockAt: Instant?) {
        val exceeded: Boolean get() = unlockAt != null
    }

    fun usedIn(intervals: List<UsageInterval>, from: Instant, to: Instant, now: Instant): Duration {
        var total = Duration.ZERO
        for (i in intervals) {
            val end = if (i.open) maxOf(i.end, now) else i.end
            val s = maxOf(i.start, from)
            val e = minOf(end, to)
            if (e > s) total = total.plus(Duration.between(s, e))
        }
        return total
    }

    fun evaluate(windows: List<QuotaWindow>, intervals: List<UsageInterval>, now: Instant): Result {
        val per = windows.map { w -> WindowUsage(w, usedIn(intervals, now.minus(w.duration), now, now)) }
        val unlock = per.filter { it.exceeded }.map { unlockAtFor(it.window, intervals, now) }.maxOrNull()
        return Result(per, unlock)
    }

    /** Plus petit t ≥ now (à la seconde) tel que l'usage sur [t - durée, t] soit strictement sous le plafond, sans nouvel usage après now. */
    private fun unlockAtFor(w: QuotaWindow, intervals: List<UsageInterval>, now: Instant): Instant {
        var lo = now
        var hi = now.plus(w.duration)
        while (lo < hi) {
            val mid = lo.plusSeconds(Duration.between(lo, hi).seconds / 2)
            if (usedIn(intervals, mid.minus(w.duration), mid, now) < w.cap) hi = mid else lo = mid.plusSeconds(1)
        }
        return lo
    }
}
