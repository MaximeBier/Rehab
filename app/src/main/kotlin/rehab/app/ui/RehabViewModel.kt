package rehab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rehab.app.di.AppGraph

data class HomeUiState(
    val streak: Int = 0,
    val best: Int = 0,
    val status: String = "…",
    val quotaLines: List<String> = emptyList(),
    val jokersLeft: Int = 0,
    val serviceConnected: Boolean = false,
    val alerts: List<String> = emptyList(),
)

enum class Tab(val label: String) { Accueil("Accueil"), Reglages("Réglages"), Journal("Journal"), Debug("Debug") }

/**
 * Le pipeline de détection (`RehabAccessibilityService`) confine `graph.usageTracker`,
 * `graph.degraded` et `graph.versionChecker.checkAll()` à son thread "rehab-engine" : ce sont des
 * classes sans synchronisation, correctes uniquement parce qu'un seul thread les touche. Ce
 * ViewModel ne les appelle donc jamais directement. Il ne lit que des vues publiées en
 * `StateFlow` par ce thread — `serviceState.connected`, `detectionState.last` (dont le champ
 * `degradedReason`, calculé sur le thread moteur) et `versionChecker.statuses` (dernier résultat
 * de `checkAll()`, également publié par le thread moteur) — `StateFlow.value` étant sûr à lire
 * depuis n'importe quel thread. Les accès base de données (streak, quota, settings) passent par
 * `Dispatchers.IO` car les adaptateurs Room sont bloquants et la base est ouverte sans
 * `allowMainThreadQueries`.
 *
 * Le rafraîchissement périodique n'est pas déclenché ici : une boucle `while(true)` dans
 * `init` tournerait tant que le `ViewModel` existe, y compris écran éteint ou app en arrière-plan
 * (une lecture Room par seconde en continu). C'est l'UI (voir `RehabApp` dans `MainActivity.kt`)
 * qui pilote l'appel à [refreshNow] sur un `repeatOnLifecycle(STARTED)`, donc uniquement quand
 * l'écran Accueil est visible.
 */
class RehabViewModel(private val graph: AppGraph) : ViewModel() {
    private val _home = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home

    fun refreshNow() { viewModelScope.launch { _home.value = withContext(Dispatchers.IO) { compute() } } }

    private fun compute(): HomeUiState {
        val now = graph.clock.now()
        val settings = graph.settingsRepo.get()
        val decision = graph.policy.evaluate(now)
        val quota = graph.policy.quotaStatus(now)
        val serviceConnected = graph.serviceState.connected.value
        val alerts = buildList {
            if (!serviceConnected) add("Rehab est inactif : active le service d'accessibilité.")
            graph.versionChecker.statuses.value.forEach { s ->
                if (!s.installed) add("${s.packageName} n'est pas installée.")
                else if (!s.inRange) add(HomeText.outOfRangeMessage(s.packageName, s.version))
            }
            val last = graph.detectionState.last.value
            when (last?.degradedReason) {
                "unknown" -> add("${last.packageName} : écrans non reconnus, mode dégradé actif.")
                "version" -> add("${last.packageName} : version hors plage testée, mode dégradé actif.")
            }
        }
        return HomeUiState(
            streak = graph.streak.current(now),
            best = graph.streak.best(now),
            status = HomeText.status(decision, graph.unlock.activeUnlockUntil(now), graph.clock.zone()),
            quotaLines = quota.perWindow.map(HomeText::quotaLine),
            jokersLeft = (settings.jokersPerDay - graph.unlock.jokersUsed(graph.schedule.dayOf(now))).coerceAtLeast(0),
            serviceConnected = serviceConnected,
            alerts = alerts,
        )
    }
}
