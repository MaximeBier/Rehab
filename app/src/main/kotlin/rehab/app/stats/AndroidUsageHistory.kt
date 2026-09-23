package rehab.app.stats

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.Duration
import java.time.Instant

/**
 * Implémentation Android de [UsageHistory] (v0.4.0) : lit `UsageStatsManager` derrière une
 * vérification de permission `AppOpsManager` (`OPSTR_GET_USAGE_STATS`), accordée par
 * l'utilisateur via `Settings.ACTION_USAGE_ACCESS_SETTINGS` (voir `Prerequisites`). Toujours
 * appelée hors thread principal par `RehabViewModel` (`Dispatchers.IO`), jamais depuis un
 * Composable (voir la doc de tête de `RehabViewModel`).
 *
 * Volontairement fine : aucune logique de dédoublonnage ni de rognage ici (voir `StatsMath.prepare`,
 * appelé par `RehabViewModel`) — cette classe ne fait que traduire un appel `UsageStatsManager` en
 * `UsageBucket`, pour rester testable trivialement (mapping pur) malgré sa dépendance Android.
 */
class AndroidUsageHistory(private val context: Context) : UsageHistory {
    private val usageStatsManager by lazy { context.getSystemService(UsageStatsManager::class.java) }
    private val appOps by lazy { context.getSystemService(AppOpsManager::class.java) }

    override fun hasPermission(): Boolean {
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun query(packageName: String, from: Instant, to: Instant, granularity: Granularity): List<UsageBucket> {
        if (!hasPermission()) return emptyList()
        val interval = when (granularity) {
            Granularity.Daily -> UsageStatsManager.INTERVAL_DAILY
            Granularity.Weekly -> UsageStatsManager.INTERVAL_WEEKLY
        }
        val stats = usageStatsManager.queryUsageStats(interval, from.toEpochMilli(), to.toEpochMilli())
        return stats.orEmpty()
            .filter { it.packageName == packageName && it.totalTimeInForeground > 0 }
            .map {
                UsageBucket(
                    start = Instant.ofEpochMilli(it.firstTimeStamp),
                    end = Instant.ofEpochMilli(it.lastTimeStamp),
                    duration = Duration.ofMillis(it.totalTimeInForeground),
                )
            }
    }
}
