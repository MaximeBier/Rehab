package rehab.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import rehab.app.ui.components.AlertBanner
import rehab.app.ui.components.HeaderCaption
import rehab.app.ui.components.KeyValue
import rehab.app.ui.components.KeyValueGroup
import rehab.app.ui.components.Note
import rehab.app.ui.components.RehabHeader
import rehab.app.ui.components.SectionLabel
import rehab.app.ui.theme.RehabColors

private fun StatTone.color(): Color = when (this) {
    StatTone.Accent -> RehabColors.Accent
    StatTone.Danger -> RehabColors.Danger
    StatTone.Neutral -> RehabColors.Text
}

@Composable
private fun StatCardView(card: StatCard) {
    SectionLabel(card.title)
    KeyValueGroup(
        listOf(
            KeyValue("Avant Rehab", card.before, valueMono = false),
            KeyValue("Maintenant", card.now, valueMono = false),
            KeyValue(card.rehabLabel, card.rehab, valueMono = false),
            KeyValue("Écart", card.change, valueColor = card.changeTone.color(), valueMono = false),
        ),
    )
}

/**
 * Mise en page de l'écran Stats (v0.4.0, spec §Écran Stats), sans dépendance au ViewModel : reçoit
 * l'état déjà chargé (`null` = premier chargement en cours) et notifie `onOpenUsageAccess` au clic
 * sur le bandeau de permission. Composable pur, testable/rendable sans Room ni Android
 * (voir RenderStats).
 */
@Composable
fun StatsContent(state: RehabViewModel.StatsUiState?, onOpenUsageAccess: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RehabHeader { HeaderCaption("7 derniers jours") }
        if (state == null) return@Column
        if (!state.hasPermission) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
                AlertBanner("Autoriser l'accès aux données d'utilisation", "Ouvrir", onOpenUsageAccess)
            }
        }
        state.apps.forEach { StatCardView(it) }
        StatCardView(state.total)
        Note("Avant = moyenne des 28 jours précédant l'installation (historique Android, ou ta saisie). Maintenant = moyenne des 7 derniers jours.")
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Wrapper avec état : charge les stats via [vm] (`loadStats`, Room + `UsageStatsManager` hors
 * thread principal) à chaque retour au premier plan sur cet onglet (`repeatOnLifecycle(STARTED)`),
 * ce qui revérifie aussi la permission d'accès aux données d'utilisation après un aller-retour dans
 * les réglages système. `StatsContent` reste un Composable pur : c'est ce wrapper, seul, qui touche
 * `vm`/`prerequisites`.
 */
@Composable
fun StatsScreen(vm: RehabViewModel, prerequisites: Prerequisites) {
    var state by remember { mutableStateOf<RehabViewModel.StatsUiState?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state = vm.loadStats()
        }
    }

    StatsContent(state) { prerequisites.openUsageAccessSettings() }
}
