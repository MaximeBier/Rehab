package rehab.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import rehab.domain.model.BlockReason
import rehab.domain.policy.PressOutcome

private val NightBg = Color(0xFF0B1020)
private val QuotaBg = Color(0xFF1E1E1E)
private val Danger = Color(0xFFE53935)
private val Calm = Color(0xFF4CAF50)

@Composable
fun BlockOverlay(state: OverlayState, nowMillis: () -> Long, onBack: () -> Unit, onHoldCompleted: () -> Unit) {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) { while (true) { now = nowMillis(); delay(1000) } }

    val danger = state.outcome is PressOutcome.Relapse
    val bg = if (danger) Color(0xFF3A0D0D) else if (state.reason == BlockReason.Night) NightBg else QuotaBg

    Column(
        Modifier.fillMaxSize().background(bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(OverlayText.reason(state, now), color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(OverlayText.streak(state), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(48.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Retour", color = Color.White) }
        Spacer(Modifier.height(24.dp))
        HoldButton(
            holdMillis = state.holdMillis,
            label = OverlayText.button(state.outcome, state.holdMillis / 1000, state.jokerMinutes),
            color = if (danger) Danger else Calm,
            onCompleted = onHoldCompleted,
        )
    }
}
