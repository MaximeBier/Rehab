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
     * Moyenne quotidienne = somme des durées des buckets ÷ durée de la période [from]..[to] en jours,
     * fractions comprises, au moins 1 jour. La période est celle de la mesure, pas les bornes des
     * buckets : un jour sans usage compte pour zéro au lieu d'être retiré du dénominateur. `null`
     * (« indisponible ») si [buckets] est vide.
     */
    fun averagePerDay(buckets: List<UsageBucket>, from: Instant, to: Instant): Duration? {
        if (buckets.isEmpty()) return null
        val totalMillis = buckets.sumOf { it.duration.toMillis() }
        val spanMillis = Duration.between(from, to).toMillis().coerceAtLeast(DAY_MILLIS)
        return Duration.ofMillis(totalMillis * DAY_MILLIS / spanMillis)
    }

    private val DAY_MILLIS = Duration.ofDays(1).toMillis()

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

    /**
     * Prépare des buckets Android bruts avant tout calcul de moyenne (fix round 1 puis 2, revue
     * v0.4.0) : dédoublonne (même borne de début — Android peut renvoyer deux fois le même bucket)
     * puis rogne chaque bucket restant à la fenêtre [from]..[to] (un bucket WEEKLY peut par exemple
     * chevaucher `installedAt`, la frontière de la fenêtre « avant »).
     *
     * La durée est répartie au prorata de la portion de portée conservée
     * (`durée × portée rognée / portée d'origine`) — **approximation** qui suppose l'usage
     * uniformément réparti à l'intérieur du bucket (Android ne donne aucune résolution plus fine).
     * Fix round 2 : le seul rognage des bornes (sans prorata) gardait la durée entière du bucket
     * alors que sa portée était réduite, ce qui gonflait artificiellement la moyenne — un bucket
     * WEEKLY à cheval sur `installedAt` faisait fuiter de l'usage post-installation dans « Avant
     * Rehab ». Un bucket qui tombe entièrement hors fenêtre est supprimé ; un bucket sans portée
     * (`start == end`, ou qui reste entièrement dans la fenêtre) garde sa durée telle quelle.
     */
    fun prepare(buckets: List<UsageBucket>, from: Instant, to: Instant): List<UsageBucket> =
        buckets
            .distinctBy { it.start }
            .mapNotNull { b ->
                val clippedStart = maxOf(b.start, from)
                val clippedEnd = minOf(b.end, to)
                if (!clippedEnd.isAfter(clippedStart)) {
                    null
                } else {
                    val originalSpanMillis = Duration.between(b.start, b.end).toMillis()
                    val clippedSpanMillis = Duration.between(clippedStart, clippedEnd).toMillis()
                    val proratedDuration = if (originalSpanMillis <= 0 || clippedSpanMillis >= originalSpanMillis) {
                        b.duration
                    } else {
                        Duration.ofMillis(b.duration.toMillis() * clippedSpanMillis / originalSpanMillis)
                    }
                    b.copy(start = clippedStart, end = clippedEnd, duration = proratedDuration)
                }
            }
}
