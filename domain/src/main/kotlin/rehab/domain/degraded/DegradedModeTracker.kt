package rehab.domain.degraded

import java.time.Duration
import java.time.Instant

class DegradedModeTracker(private val threshold: Duration = Duration.ofSeconds(30)) {
    private val unknownSince = mutableMapOf<String, Instant>()
    private val degradedByUnknown = mutableSetOf<String>()
    private val versionOutOfRange = mutableSetOf<String>()

    fun onVersionCheck(packageName: String, inRange: Boolean) {
        if (inRange) versionOutOfRange -= packageName else versionOutOfRange += packageName
    }

    /**
     * [unknownScreen] doit être vrai dès que l'écran n'est pas reconnu **ou** que le snapshot a été tronqué
     * (`Snapshot.truncated`) : un arbre amputé est indiscernable de « rien à bloquer » et doit être traité en
     * échec fermé (spec §2.8), pas en silence. Auparavant, ce compteur exigeait aussi `watching` (l'onglet
     * Accueil sélectionné) : ce drapeau n'a de sens que pour la cible Instagram Accueil, donc sur X — ou sur
     * tout écran Instagram hors Accueil — `unknownScreen` pouvait rester vrai indéfiniment sans jamais armer
     * le compteur, rendant le mode dégradé "unknown" inatteignable sur ces écrans.
     */
    fun onDetection(packageName: String, unknownScreen: Boolean, now: Instant) {
        if (!unknownScreen) {
            unknownSince.remove(packageName)
            return
        }
        val since = unknownSince.getOrPut(packageName) { now }
        if (Duration.between(since, now) >= threshold) degradedByUnknown += packageName
    }

    fun isDegraded(packageName: String) = reason(packageName) != null

    fun reason(packageName: String): String? = when {
        packageName in versionOutOfRange -> "version"
        packageName in degradedByUnknown -> "unknown"
        else -> null
    }
}
