package rehab.app.stats

import rehab.app.AppDisplayNames
import rehab.domain.model.TargetId
import rehab.rules.catalog.InstagramRules
import rehab.rules.catalog.TwitterRules

/**
 * Une app couverte par l'onglet Stats et la section « Avant Rehab » des Réglages : paquet, cibles Rehab
 * mesurées et libellé de la ligne « Dont … ». Instagram a des Reels, X n'en a pas, d'où un libellé par app.
 */
data class StatsApp(val packageName: String, val targets: List<TargetId>, val rehabLabel: String) {
    val label: String get() = AppDisplayNames.of(packageName)
}

/** Source unique des apps Stats, dans l'ordre d'affichage. */
object StatsApps {
    val all = listOf(
        StatsApp(InstagramRules.PACKAGE, listOf(InstagramRules.REELS, InstagramRules.SUGGESTED), "Dont fil et Reels"),
        StatsApp(TwitterRules.PACKAGE, listOf(TwitterRules.APP, TwitterRules.HOME), "Dont hors DM"),
    )
}
