package rehab.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rehab.app.ui.components.GaugeCard
import rehab.app.ui.components.AlertBanner
import rehab.app.ui.components.RecordRing
import rehab.app.ui.components.RehabHeader
import rehab.app.ui.components.RehabText
import rehab.app.ui.components.StatusPill
import rehab.app.ui.components.withMonoNumbers
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors

// internal (pas private) : réutilisé par OnboardingScreen (tâche 8) pour les mêmes tons de pastille.
internal fun PillTone.color(): Color = when (this) {
    PillTone.Accent -> RehabColors.Accent
    PillTone.Muted -> RehabColors.Muted
    PillTone.Warn -> RehabColors.Warn
}

private fun AlertAction.label(): String = when (this) {
    AlertAction.OpenAccessibility -> "Activer"
    AlertAction.OpenDebug -> "Debug"
}

@Composable
fun HomeScreen(state: HomeUiState, onAlertAction: (AlertAction) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RehabHeader { StatusPill(state.pill.text, state.pill.tone.color()) }
        if (!state.loaded) return@Column
        if (state.alerts.isNotEmpty()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.alerts.forEach { a -> AlertBanner(a.text, a.action?.label()) { a.action?.let(onAlertAction) } }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val s = state.streak
            if (s.inRecord) {
                RecordRing(220.dp, 12.dp, RehabColors.Accent) {
                    Text("RECORD", style = RehabText.ringLabel.copy(color = RehabColors.Accent))
                    Spacer(Modifier.height(4.dp))
                    Text("${s.current}", style = TextStyle(fontFamily = ChivoMono, fontSize = 84.sp, fontWeight = FontWeight.W300, lineHeight = 84.sp, color = RehabColors.Text))
                    Spacer(Modifier.height(4.dp))
                    Text(HomeText.unit(s.current), style = RehabText.ringUnit)
                }
            } else {
                // Décision de cadrage (spec §2.1) : hors record, arc partiel série / record sur piste panel.
                RecordRing(180.dp, 12.dp, RehabColors.Accent, fraction = HomeText.ringFraction(s), trackColor = RehabColors.Panel) {
                    Text("${s.current}", style = TextStyle(fontFamily = ChivoMono, fontSize = 68.sp, fontWeight = FontWeight.W300, lineHeight = 68.sp, color = RehabColors.Text))
                    Spacer(Modifier.height(4.dp))
                    Text(HomeText.unit(s.current), style = RehabText.ringUnit)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (state.statusLine ?: HomeText.recordLine(s))?.let { Text(withMonoNumbers(it), style = RehabText.body13, textAlign = TextAlign.Center) }
                Text(withMonoNumbers(HomeText.metaLine(s, state.jokersLeft, state.jokersPerDay).uppercase(), RehabColors.Text), style = RehabText.caps12, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.gauges.forEach { GaugeCard(it.label, it.value, it.fraction, it.exceeded) }
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.night.label.uppercase(), style = RehabText.caps12)
                Text(state.night.value, style = RehabText.caps12.copy(fontFamily = ChivoMono, color = RehabColors.Text))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
