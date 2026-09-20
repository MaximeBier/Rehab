package rehab.app

import rehab.rules.catalog.InstagramRules
import rehab.rules.catalog.TwitterRules

/**
 * Nom d'affichage d'une app du catalogue, pour l'UI et les notifications. Factorisé : ce mapping était
 * dupliqué entre `RulesNotifier.kt` et `HomeText.kt` (revue finale, mineur).
 */
object AppDisplayNames {
    fun of(packageName: String): String = when (packageName) {
        InstagramRules.PACKAGE -> "Instagram"
        TwitterRules.PACKAGE -> "X"
        else -> packageName
    }
}
