package rehab.app.stats

import java.time.Instant

/**
 * Granularité demandée à `UsageStatsManager`. Fix round 1 (revue v0.4.0) : `INTERVAL_BEST` laisse
 * Android choisir librement parmi plusieurs granularités qui se chevauchent (jour/semaine/mois),
 * ce qui double-compte le temps d'utilisation ; on impose donc une granularité unique par fenêtre
 * interrogée. `Daily` : buckets journaliers, retenus par Android environ 7 jours seulement — utilisé
 * pour la fenêtre « maintenant » (7 derniers jours). `Weekly` : buckets hebdomadaires, retenus plus
 * longtemps — utilisé pour la fenêtre « avant » (28 jours précédant l'installation).
 */
enum class Granularity { Daily, Weekly }

/**
 * Historique d'utilisation Android (`UsageStatsManager`), derrière une interface testable sans
 * Android : `AndroidUsageHistory` est la seule implémentation, câblée dans `AppGraph`. Les buckets
 * bruts qu'elle retourne peuvent chevaucher ou déborder la fenêtre interrogée : c'est à l'appelant
 * de les passer par `StatsMath.prepare` (dédoublonnage + rognage aux bornes) avant tout calcul de
 * moyenne — `AndroidUsageHistory` reste volontairement sans cette logique (voir sa doc).
 */
interface UsageHistory {
    /** `true` si Rehab a la permission d'accès aux données d'utilisation (`PACKAGE_USAGE_STATS`). */
    fun hasPermission(): Boolean

    /**
     * Buckets d'utilisation bruts de [packageName] entre [from] et [to] à la granularité
     * [granularity], temps app entière (DM compris). Liste vide si la permission manque : ne jette
     * jamais. Peuvent chevaucher ou déborder [from]/[to] (comportement `UsageStatsManager`) —
     * voir `StatsMath.prepare`.
     */
    fun query(packageName: String, from: Instant, to: Instant, granularity: Granularity): List<UsageBucket>
}
