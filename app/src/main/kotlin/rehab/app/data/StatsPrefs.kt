package rehab.app.data

import android.content.SharedPreferences

/**
 * Saisie manuelle « avant Rehab » (minutes/jour) par app (v0.4.0 — écran Stats/Réglages), stockée
 * dans les SharedPreferences `rehab_stats` et non dans `Settings` du domaine : ce réglage ne
 * desserre aucun verrou, il n'a donc pas à passer par `SettingsGuard` (même raisonnement que
 * `RedirectPrefs`).
 *
 * `null` = pas de saisie : la comparaison retombe sur l'historique Android (`StatsMath.effectiveBefore`).
 */
class StatsPrefs(private val prefs: SharedPreferences) {

    /** Minutes/jour saisies pour [packageName], ou `null` si non renseigné. */
    fun get(packageName: String): Int? {
        val v = prefs.getInt(key(packageName), -1)
        return if (v < 0) null else v
    }

    /** `null` efface la saisie (retour à la valeur Android). */
    fun set(packageName: String, minutesPerDay: Int?) {
        val edit = prefs.edit()
        if (minutesPerDay == null) edit.remove(key(packageName)) else edit.putInt(key(packageName), minutesPerDay)
        edit.apply()
    }

    private fun key(packageName: String) = "before_$packageName"
}
