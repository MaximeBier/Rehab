package rehab.app.service

import android.content.pm.PackageManager
import rehab.domain.degraded.DegradedModeTracker
import rehab.domain.model.Event
import rehab.domain.ports.EventLog
import rehab.domain.time.Clock
import rehab.rules.RuleCatalog
import java.time.Duration

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
        // Capturé avant la mutation : sert à détecter la transition « en plage / hors plage »,
        // seul moment où l'on doit (re)notifier — y compris si l'app repasse en plage puis en
        // ressort avec la même version.
        val wasOutOfRangeOnVersion = degraded.reason(pkg) == "version"
        val inRange = rules.testedVersions.contains(version)
        degraded.onVersionCheck(pkg, inRange)
        if (!inRange && !wasOutOfRangeOnVersion) {
            // Lecture bornée (et non events.all()) : garde-fou contre un double appel très
            // rapproché de checkAll() pour la même entrée hors plage, sans bloquer un nouveau
            // cycle en-plage -> hors-plage plus tard (couvert par wasOutOfRangeOnVersion ci-dessus).
            val recentlyLogged = events.since(clock.now().minus(DEDUPE_WINDOW))
                .any { it is Event.RulesOutOfRange && it.packageName == pkg && it.version == version }
            if (!recentlyLogged) {
                events.append(Event.RulesOutOfRange(clock.now(), pkg, version))
                notifier.notifyOutOfRange(pkg, version)
            }
        }
        AppStatus(pkg, installed = true, version = version, inRange = inRange)
    }

    fun versionOf(packageName: String): String = versions.versionOf(packageName) ?: "?"

    private companion object {
        val DEDUPE_WINDOW: Duration = Duration.ofSeconds(5)
    }
}
