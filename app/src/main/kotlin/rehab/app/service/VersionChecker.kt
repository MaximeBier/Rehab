package rehab.app.service

import android.content.pm.PackageManager
import rehab.domain.degraded.DegradedModeTracker
import rehab.domain.model.Event
import rehab.domain.ports.EventLog
import rehab.domain.time.Clock
import rehab.rules.RuleCatalog

fun interface InstalledVersions {
    fun versionOf(packageName: String): String?
}

class PackageManagerVersions(private val pm: PackageManager) : InstalledVersions {
    override fun versionOf(packageName: String): String? =
        runCatching { pm.getPackageInfo(packageName, 0).versionName }.getOrNull()
}

data class AppStatus(val packageName: String, val installed: Boolean, val version: String?, val inRange: Boolean)

class VersionChecker(
    private val versions: InstalledVersions,
    private val catalog: RuleCatalog,
    private val degraded: DegradedModeTracker,
    private val events: EventLog,
    private val notifier: RulesNotifier,
    private val clock: Clock,
) {
    fun checkAll(): List<AppStatus> = catalog.packageNames.map { pkg ->
        val rules = catalog.forPackage(pkg)!!
        val version = versions.versionOf(pkg)
        if (version == null) {
            degraded.onVersionCheck(pkg, inRange = true)
            return@map AppStatus(pkg, installed = false, version = null, inRange = false)
        }
        val inRange = rules.testedVersions.contains(version)
        degraded.onVersionCheck(pkg, inRange)
        if (!inRange) {
            val alreadyLogged = events.all().any { it is Event.RulesOutOfRange && it.packageName == pkg && it.version == version }
            if (!alreadyLogged) {
                events.append(Event.RulesOutOfRange(clock.now(), pkg, version))
                notifier.notifyOutOfRange(pkg, version)
            }
        }
        AppStatus(pkg, installed = true, version = version, inRange = inRange)
    }

    fun versionOf(packageName: String): String = versions.versionOf(packageName) ?: "?"
}
