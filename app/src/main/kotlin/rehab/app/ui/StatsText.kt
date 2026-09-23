package rehab.app.ui

import rehab.app.stats.StatsMath
import java.time.Duration

/** Ton de la ligne « Écart » d'une carte Stats : accent si baisse, danger si hausse, neutre sinon/inconnu. */
enum class StatTone { Accent, Danger, Neutral }

data class StatCard(
    val title: String,
    val before: String,
    val now: String,
    /** Libellé de la ligne « Dont … » : varie par app (Instagram a des Reels, X n'en a pas). */
    val rehabLabel: String,
    val rehab: String,
    val change: String,
    val changeTone: StatTone,
)

/**
 * Textes et formatage de l'onglet Stats (v0.4.0), en français. Toute la lecture Android
 * (`UsageHistory`, `graph.usageLog`) est faite par `RehabViewModel.loadStats` ; cet objet ne fait
 * que transformer des `Duration?` déjà calculées en texte d'affichage — pur, testable sans Android.
 */
object StatsText {
    private const val UNAVAILABLE = "accès requis"
    private const val TO_FILL = "à renseigner"

    fun perDay(d: Duration): String = "${HomeText.duration(d)}/j"

    /**
     * Carte d'une app (ou du bloc Total) pour l'écran Stats. [beforeSource] vaut « Android » ou
     * « saisi » (mention affichée à côté de « Avant Rehab »), `null` si aucune source (Total, ou
     * valeur indisponible/à renseigner). [rehabLabel] est le libellé de la ligne « Dont … » (varie
     * par app : Instagram a des Reels, X n'en a pas — voir `RehabViewModel.statsApps`).
     */
    fun card(
        title: String,
        rehabLabel: String,
        before: Duration?,
        beforeSource: String?,
        now: Duration?,
        rehab: Duration,
        hasPermission: Boolean,
    ): StatCard {
        val beforeText = before?.let { perDay(it) + (beforeSource?.let { s -> " · $s" } ?: "") }
            ?: if (hasPermission) TO_FILL else UNAVAILABLE
        val nowText = now?.let { perDay(it) } ?: UNAVAILABLE
        val change = if (before != null && now != null) StatsMath.changePercent(before, now) else null
        // Signe typographique (U+2212), pas le trait d'union ASCII : spec §Écran Stats (« −73 % »).
        val changeText = change?.let { c ->
            when {
                c > 0 -> "+$c %"
                c < 0 -> "−${-c} %"
                else -> "0 %"
            }
        } ?: "—"
        val tone = when {
            change == null -> StatTone.Neutral
            change < 0 -> StatTone.Accent
            change > 0 -> StatTone.Danger
            else -> StatTone.Neutral
        }
        return StatCard(title, beforeText, nowText, rehabLabel, perDay(rehab), changeText, tone)
    }

    /**
     * Valeur affichée dans la section « Avant Rehab » des Réglages : la saisie manuelle si
     * renseignée (sans mention — elle est par nature la valeur choisie), sinon l'historique Android
     * avec sa mention, sinon « à renseigner ». Contrairement à [card], jamais « accès requis » : ici
     * l'utilisateur peut toujours renseigner la valeur à la main.
     */
    fun settingsBeforeValue(manual: Duration?, android: Duration?): String = when {
        manual != null -> perDay(manual)
        android != null -> "${perDay(android)} · Android"
        else -> TO_FILL
    }
}
