package rehab.app.service

import kotlinx.coroutines.flow.MutableStateFlow

data class LastDetection(
    val packageName: String,
    val appVersion: String,
    val target: String?,
    val screenId: String?,
    val unknownScreen: Boolean,
    val degraded: Boolean,
    val atMillis: Long,
    /**
     * Raison du mode dégradé ("version" | "unknown" | null), lue une fois pour toutes sur le
     * thread "rehab-engine" via [rehab.domain.degraded.DegradedModeTracker.reason] puis publiée
     * ici. Un lecteur hors moteur (ex. [rehab.app.ui.RehabViewModel]) ne doit jamais appeler
     * `DegradedModeTracker.reason` lui-même : cette classe n'est pas synchronisée et n'est sûre
     * que confinée à un seul thread. Passer par ce champ, qui traverse un StateFlow.
     */
    val degradedReason: String? = null,
)

class DetectionState { val last = MutableStateFlow<LastDetection?>(null) }

/**
 * [initiallyConnected] devrait toujours venir de `Prerequisites.accessibilityEnabled()` (source fiable côté
 * système), pas d'une valeur figée à `false` : au démarrage du processus, avant toute connexion du service
 * d'accessibilité, `false` fait afficher à tort le bandeau « Rehab est inactif » même quand le service tourne
 * déjà côté système et se reconnectera dans l'instant (revue finale, mineur). Voir `AppGraph`.
 */
class ServiceState(initiallyConnected: Boolean = false) { val connected = MutableStateFlow(initiallyConnected) }
