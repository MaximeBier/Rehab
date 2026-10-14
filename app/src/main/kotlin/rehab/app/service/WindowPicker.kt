package rehab.app.service

/**
 * Descripteur d'une fenêtre à l'écran, extrait d'`AccessibilityWindowInfo` pour choisir sans Android.
 * [packageName] est nul quand la racine de la fenêtre est illisible (transition, vidéo plein écran).
 */
data class WindowCandidate(
    val windowId: Int,
    val isApplication: Boolean,
    val packageName: String?,
    val active: Boolean,
    val focused: Boolean,
    val area: Long,
)

data class PickedWindow(val windowId: Int, val packageName: String)

/**
 * v0.7.0 : `rootInActiveWindow` seul ratait l'app quand la fenêtre active était ailleurs (écran partagé, PiP) ou
 * momentanément illisible — le service croyait alors qu'on avait quitté l'app et arrêtait son ticker. On regarde
 * toutes les fenêtres d'application : l'active si elle est suivie, sinon la focalisée, sinon la plus grande.
 * Une fenêtre à racine illisible garde le paquet vu précédemment pour son `windowId` ([lastKnown]).
 */
object WindowPicker {
    fun pick(windows: List<WindowCandidate>, tracked: Set<String>, lastKnown: Map<Int, String>): PickedWindow? {
        val apps = windows.filter { it.isApplication }
            .mapNotNull { w -> (w.packageName ?: lastKnown[w.windowId])?.takeIf { it in tracked }?.let { w to it } }
        val chosen = apps.firstOrNull { it.first.active }
            ?: apps.firstOrNull { it.first.focused }
            ?: apps.maxByOrNull { it.first.area }
            ?: return null
        return PickedWindow(chosen.first.windowId, chosen.second)
    }
}
