package rehab.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Ouvre la liste des conversations d'une app (lien « Ouvrir mes messages » de l'écran de blocage, v0.7.1) par son
 * adresse directe (`PackageRules.messagesUri`). Le manifeste déclare Instagram et X dans `<queries>` : sans ça,
 * [canOpen] répondrait toujours faux (visibilité des paquets).
 */
class MessagesLauncher(private val context: Context) {

    fun intentFor(packageName: String, uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Faux sans adresse ou si Android ne trouve aucune activité : le lien n'est alors pas affiché. */
    fun canOpen(packageName: String, uri: String?): Boolean =
        // MATCH_DEFAULT_ONLY : startActivity exige CATEGORY_DEFAULT ; sans ce filtre, canOpen pourrait dire vrai
        // pour une activité que startActivity refuserait (lien affiché qui ne fait rien).
        uri != null && context.packageManager.resolveActivity(intentFor(packageName, uri), PackageManager.MATCH_DEFAULT_ONLY) != null

    /** Dernier résultat de [canOpen] par paquet, avec la version de l'app pour laquelle il vaut. Thread "rehab-engine". */
    private val availability = HashMap<String, Pair<String, Boolean>>()

    /**
     * [canOpen] mémorisé par paquet et version de l'app : l'état de l'overlay est recalculé à chaque tick (1 s) pendant
     * un blocage, inutile de réinterroger le gestionnaire de paquets. Une mise à jour de l'app change sa version et
     * relance la vérification. Appelé uniquement depuis le thread "rehab-engine".
     */
    fun isAvailable(packageName: String, uri: String?, appVersion: String): Boolean {
        availability[packageName]?.takeIf { it.first == appVersion }?.let { return it.second }
        return canOpen(packageName, uri).also { availability[packageName] = appVersion to it }
    }

    /** Lance l'ouverture ; faux en cas d'échec (l'overlay reste alors affiché), jamais d'exception. */
    fun open(packageName: String, uri: String?): Boolean {
        if (uri == null || !canOpen(packageName, uri)) return false
        return runCatching { context.startActivity(intentFor(packageName, uri)) }
            .onFailure { Log.e("Rehab", "Ouverture des messages impossible ($packageName)", it) }
            .isSuccess
    }
}
