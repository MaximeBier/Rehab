package rehab.domain.degraded

/**
 * v0.7.0 : avec "tout sauf DM", un écran inconnu est compté ; seul le hors-plage de version reste signalé.
 */
class DegradedModeTracker {
    private val versionOutOfRange = mutableSetOf<String>()

    fun onVersionCheck(packageName: String, inRange: Boolean) {
        if (inRange) versionOutOfRange -= packageName else versionOutOfRange += packageName
    }

    fun isDegraded(packageName: String) = reason(packageName) != null

    fun reason(packageName: String): String? = if (packageName in versionOutOfRange) "version" else null
}
