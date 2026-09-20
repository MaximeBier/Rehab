package rehab.domain.degraded

import java.time.Duration
import java.time.Instant

class DegradedModeTracker(private val threshold: Duration = Duration.ofSeconds(30)) {
    private val unknownSince = mutableMapOf<String, Instant>()
    private val degradedByUnknown = mutableSetOf<String>()
    private val versionOutOfRange = mutableSetOf<String>()

    fun onVersionCheck(packageName: String, inRange: Boolean) {
        if (inRange) versionOutOfRange -= packageName else versionOutOfRange += packageName
    }

    fun onDetection(packageName: String, unknownScreen: Boolean, watching: Boolean, now: Instant) {
        if (!unknownScreen || !watching) {
            unknownSince.remove(packageName)
            return
        }
        val since = unknownSince.getOrPut(packageName) { now }
        if (Duration.between(since, now) >= threshold) degradedByUnknown += packageName
    }

    fun isDegraded(packageName: String) = reason(packageName) != null

    fun reason(packageName: String): String? = when {
        packageName in versionOutOfRange -> "version"
        packageName in degradedByUnknown -> "unknown"
        else -> null
    }
}
