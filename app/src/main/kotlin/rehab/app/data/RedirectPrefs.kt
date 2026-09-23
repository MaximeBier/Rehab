package rehab.app.data

import android.content.SharedPreferences
import rehab.rules.RedirectTab

/**
 * Onglet de repli par app pour la bascule au blocage quota (v0.3.0), stocké dans les SharedPreferences
 * `rehab_redirect` et non dans `Settings` du domaine : ce réglage ne desserre aucun verrou (désactiver la
 * bascule ramène l'overlay), il n'a donc pas à passer par `SettingsGuard`.
 *
 * `null` = bascule désactivée (overlay comme avant). Défaut : [RedirectTab.Messages]. Lu par le thread
 * "rehab-engine" à chaque blocage et écrit par le ViewModel (Dispatchers.IO) : `SharedPreferences` est
 * sûr entre threads, et `apply()` met à jour la copie mémoire avant de rendre la main.
 */
class RedirectPrefs(private val prefs: SharedPreferences) {

    fun get(packageName: String): RedirectTab? = when (val v = prefs.getString(key(packageName), null)) {
        OFF -> null
        null -> RedirectTab.Messages
        else -> RedirectTab.entries.firstOrNull { it.name == v } ?: RedirectTab.Messages
    }

    fun set(packageName: String, tab: RedirectTab?) {
        prefs.edit().putString(key(packageName), tab?.name ?: OFF).apply()
    }

    private fun key(packageName: String) = "tab_$packageName"

    private companion object { const val OFF = "off" }
}
