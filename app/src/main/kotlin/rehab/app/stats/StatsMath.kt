package rehab.app.stats

import java.time.Duration
import java.time.Instant

/**
 * Un intervalle d'utilisation déjà agrégé : soit un bucket Android (`UsageStatsManager`,
 * `INTERVAL_BEST`), soit un intervalle Rehab (`UsageInterval` converti par l'appelant). [duration]
 * est fournie par l'appelant plutôt que recalculée ici, pour couvrir le cas d'un intervalle Rehab
 * encore ouvert (borne haute = l'instant présent, pas `end`).
 */
data class UsageBucket(val start: Instant, val end: Instant, val duration: Duration)

/**
 * Calculs de l'onglet Stats (v0.4.0 — spec §Technique), purs et testables sans Android : moyenne
 * quotidienne à partir de buckets d'utilisation, écart en % entre deux moyennes, et sélection de la
 * valeur « avant Rehab » effective (saisie manuelle en priorité sur l'historique Android).
 */
object StatsMath {
    /**
     * Moyenne quotidienne = somme des durées des buckets ÷ nombre de jours réellement couverts
     * (bornes min/max des buckets, au moins 1 jour dès qu'un bucket existe, plafonné à [maxDays] —
     * la fenêtre de temps réellement interrogée). `null` (« indisponible ») si [buckets] est vide.
     */
    fun averagePerDay(buckets: List<UsageBucket>, maxDays: Int): Duration? {
        if (buckets.isEmpty()) return null
        val total = buckets.fold(Duration.ZERO) { acc, b -> acc + b.duration }
        val earliest = buckets.minOf { it.start }
        val latest = buckets.maxOf { it.end }
        val coveredDays = Duration.between(earliest, latest).toDays().coerceAtLeast(1).coerceAtMost(maxDays.toLong())
        return total.dividedBy(coveredDays)
    }

    /**
     * Écart en % arrondi entre [before] et [after] : `(after − before) / before × 100`. `null`
     * (affiché « — ») si [before] est inconnu (`null`) ou nul (division impossible).
     */
    fun changePercent(before: Duration?, after: Duration): Int? {
        if (before == null || before.isZero) return null
        val diffMillis = after.toMillis() - before.toMillis()
        return Math.round(diffMillis * 100.0 / before.toMillis()).toInt()
    }

    /** Valeur « avant Rehab » effective (spec §Sources) : la saisie manuelle si renseignée, sinon l'historique Android. */
    fun effectiveBefore(manual: Duration?, android: Duration?): Duration? = manual ?: android
}
