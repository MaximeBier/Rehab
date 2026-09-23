package rehab.app.stats

import java.time.Instant

/**
 * Historique d'utilisation Android (`UsageStatsManager`), derrière une interface testable sans
 * Android : `AndroidUsageHistory` est la seule implémentation, câblée dans `AppGraph`.
 */
interface UsageHistory {
    /** `true` si Rehab a la permission d'accès aux données d'utilisation (`PACKAGE_USAGE_STATS`). */
    fun hasPermission(): Boolean

    /**
     * Buckets d'utilisation (`INTERVAL_BEST`) de [packageName] entre [from] et [to], temps app
     * entière (DM compris). Liste vide si la permission manque : ne jette jamais.
     */
    fun query(packageName: String, from: Instant, to: Instant): List<UsageBucket>
}
