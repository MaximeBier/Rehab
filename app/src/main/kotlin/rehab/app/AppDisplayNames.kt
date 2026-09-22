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

    /** Nom d'affichage d'une cible (`TargetId.value`) du catalogue, pour le Journal (tâche 5). */
    fun target(id: String): String = when (id) {
        InstagramRules.REELS.value -> "Instagram · Reels"
        InstagramRules.SUGGESTED.value -> "Instagram · Suggéré"
        TwitterRules.HOME.value -> "X · Accueil"
        else -> id
    }
}
