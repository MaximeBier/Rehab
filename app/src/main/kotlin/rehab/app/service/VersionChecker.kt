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
    // Dernière version pour laquelle on a notifié durant l'épisode "hors plage" en cours pour
    // ce package ; null tant que le package est en plage (ou pas encore vérifié). Remis à null
    // dès qu'on revoit le package en plage, ce qui permet de renotifier si l'app en ressort à
    // nouveau, même avec la même version. Clé de dédoublonnage = (package, version), pas le seul
    // état "hors plage" : sinon un changement de version pendant un épisode hors-plage continu
    // (ex. 400 -> 410, jamais repassé en plage entre les deux) ne serait jamais signalé.
    private val lastNotifiedVersion = mutableMapOf<String, String?>()

    fun checkAll(): List<AppStatus> = catalog.packageNames.map { pkg ->
        val rules = catalog.forPackage(pkg)!!
        val version = versions.versionOf(pkg)
        if (version == null) {
            degraded.onVersionCheck(pkg, inRange = true)
            lastNotifiedVersion[pkg] = null
            return@map AppStatus(pkg, installed = false, version = null, inRange = false)
        }
        val inRange = rules.testedVersions.contains(version)
        degraded.onVersionCheck(pkg, inRange)
        if (inRange) {
            lastNotifiedVersion[pkg] = null
        } else if (lastNotifiedVersion[pkg] != version) {
            // Lecture bornée (et non events.all()) : garde-fou contre un double appel très
            // rapproché de checkAll() pour le même (package, version), sans dépendre d'un état
            // en mémoire qui ne survivrait pas à un redémarrage du processus dans cette fenêtre.
            val recentlyLogged = events.since(clock.now().minus(DEDUPE_WINDOW))
                .any { it is Event.RulesOutOfRange && it.packageName == pkg && it.version == version }
            if (!recentlyLogged) {
                events.append(Event.RulesOutOfRange(clock.now(), pkg, version))
                notifier.notifyOutOfRange(pkg, version)
            }
            lastNotifiedVersion[pkg] = version
        }
        AppStatus(pkg, installed = true, version = version, inRange = inRange)
    }

    fun versionOf(packageName: String): String = versions.versionOf(packageName) ?: "?"

    private companion object {
        val DEDUPE_WINDOW: Duration = Duration.ofSeconds(5)
    }
}
