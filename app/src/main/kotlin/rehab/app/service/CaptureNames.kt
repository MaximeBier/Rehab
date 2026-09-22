package rehab.app.service

import rehab.rules.catalog.InstagramRules
import rehab.rules.catalog.TwitterRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Noms de fichiers des captures de structure (écran Debug), lisibles sans avoir besoin d'ouvrir le JSON. */
object CaptureNames {
    private val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")

    /**
     * `ig`/`x` pour Instagram/X (sinon dernier segment du nom de package), suivi de l'écran
     * (`screenId` sans son préfixe d'app ; `home` devient `feed` pour Instagram uniquement, faute
     * de quoi le nom serait ambigu avec le fil d'actualité de X) puis de l'horodatage local.
     */
    fun fileName(packageName: String, screenId: String?, capturedAtMillis: Long, zone: ZoneId): String {
        val prefix = when (packageName) {
            InstagramRules.PACKAGE -> "ig"
            TwitterRules.PACKAGE -> "x"
            else -> packageName.substringAfterLast('.')
        }
        val rawScreen = screenId?.substringAfter('.')
        val screen = when {
            rawScreen == null -> "inconnu"
            packageName == InstagramRules.PACKAGE && rawScreen == "home" -> "feed"
            else -> rawScreen
        }
        val ts = Instant.ofEpochMilli(capturedAtMillis).atZone(zone).format(timestamp)
        return "${prefix}_${screen}_$ts.json"
    }
}
