package rehab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import rehab.app.ui.components.HeaderCaption
import rehab.app.ui.components.Note
import rehab.app.ui.components.RehabHeader
import rehab.app.ui.components.RehabText
import rehab.app.ui.theme.RehabColors

/** Couleur du point + du libellé : identiques pour Danger/Accent/Warn, sauf Neutral (point Muted, libellé Text). */
private fun JournalTone.dotColor(): Color = when (this) {
    JournalTone.Danger -> RehabColors.Danger
    JournalTone.Accent -> RehabColors.Accent
    JournalTone.Warn -> RehabColors.Warn
    JournalTone.Neutral -> RehabColors.Muted
}

private fun JournalTone.labelColor(): Color = when (this) {
    JournalTone.Danger -> RehabColors.Danger
    JournalTone.Accent -> RehabColors.Accent
    JournalTone.Warn -> RehabColors.Warn
    JournalTone.Neutral -> RehabColors.Text
}

/**
 * Mise en page de l'écran Journal (`docs/design/mockups/Journal.dc.html`), sans dépendance au
 * ViewModel : reçoit les journées déjà chargées et groupées (voir `JournalText`). Composable pur,
 * testable/rendable sans Room ni coroutine (voir RenderJournal).
 */
@Composable
fun JournalContent(days: List<JournalDay>) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { RehabHeader { HeaderCaption("30 jours · brut") } }
        if (days.isEmpty()) {
            item { Note("Aucun événement sur les 30 derniers jours.") }
        } else {
            days.forEachIndexed { index, day ->
                item {
                    Text(
                        day.header.uppercase(),
                        style = RehabText.caps12,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = if (index == 0) 6.dp else 18.dp, bottom = 8.dp),
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(16.dp))) {
                        day.rows.forEachIndexed { rowIndex, row ->
                            JournalRowView(row)
                            if (rowIndex != day.rows.lastIndex) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(RehabColors.Bg))
                            }
                        }
                    }
                }
            }
            item { Note("Intervalles de moins de 2 s supprimés. Le temps passé sous joker ou relapse est compté.") }
        }
    }
}

@Composable
private fun JournalRowView(row: JournalRow) {
    Row(
        Modifier.fillMaxWidth().background(RehabColors.Panel).padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.time, style = RehabText.mono13.copy(color = RehabColors.Muted), modifier = Modifier.width(52.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(row.tone.dotColor()))
                Text(row.label, style = RehabText.body14.copy(color = row.tone.labelColor()))
            }
            Text(row.type, style = RehabText.small11)
        }
        Text(row.detail, style = RehabText.mono13.copy(color = RehabColors.Muted), modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * Wrapper avec état : charge les journées via [vm] (`loadJournal`, Room + horloge hors thread
 * principal) puis les recharge toutes les 10 s tant que l'écran est visible
 * (`repeatOnLifecycle(STARTED)`), même principe que `SettingsScreen`/`RehabApp`. `JournalContent`
 * reste un Composable pur : c'est ce wrapper, seul, qui touche `graph` via le ViewModel.
 */
@Composable
fun JournalScreen(vm: RehabViewModel) {
    var days by remember { mutableStateOf<List<JournalDay>>(emptyList()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                days = vm.loadJournal()
                delay(10_000)
            }
        }
    }

    JournalContent(days)
}
