package rehab.app.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import rehab.app.ui.theme.Chivo
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors

enum class HoldKind { Joker, Relapse }

/**
 * Bouton d'appui long de l'overlay (DESIGN §3, `Etats-Bouton`) : rendu et geste seulement, sans horloge.
 * [progress] ∈ [0, 1] est piloté par [BlockOverlay] ; [onPressChange] signale l'appui et le relâchement.
 * Au repos la jauge est absente (pas d'arc de longueur 0). [scale] : effet relapse (DESIGN §5).
 */
@Composable
fun HoldButton(
    kind: HoldKind,
    progress: Float,
    secondsLeft: Int,
    done: Boolean,
    doneValue: String,
    scale: Float,
    onPressChange: (Boolean) -> Unit,
) {
    val relapse = kind == HoldKind.Relapse
    val onPress by rememberUpdatedState(onPressChange)
    val disc = when {
        done && relapse -> RehabColors.Danger
        done -> RehabColors.Accent
        relapse -> RehabColors.DangerDeep
        else -> RehabColors.Bg
    }
    val track = if (relapse) RehabColors.DangerTrack else RehabColors.Line
    val gauge = if (relapse) RehabColors.Danger else RehabColors.Accent
    Box(
        Modifier
            .size(96.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(disc)
            .pointerInput(done) {
                if (done) return@pointerInput
                detectTapGestures(onPress = {
                    onPress(true)
                    tryAwaitRelease()
                    onPress(false)
                })
            }
            .semantics { contentDescription = if (relapse) "Maintenir pour un relapse" else "Maintenir pour un joker" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = 8.dp.toPx()
            val inset = w / 2 + 4.dp.toPx()
            val tl = Offset(inset, inset)
            val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
            // Terminé : le disque plein de la même couleur que la jauge suffit.
            if (!done) drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
            // Extrémités rondes comme Etats-Bouton ; jamais dessinée à 0 (sinon un point, DESIGN §3).
            if (!done && progress > 0f) drawArc(gauge, -90f, 360f * progress.coerceAtMost(1f), false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                done -> {
                    BigValue(doneValue, RehabColors.Bg)
                    SubLabel("MIN", RehabColors.Bg)
                }
                progress > 0f -> {
                    BigValue("$secondsLeft", if (relapse) RehabColors.DangerText else RehabColors.Text)
                    // #D9A9A2 dans la maquette : approché par danger-text à 70 % pour rester dans les tokens.
                    SubLabel("SEC", if (relapse) RehabColors.DangerText.copy(alpha = 0.7f) else RehabColors.Muted)
                }
                else -> Text(
                    if (relapse) "RELAPSE" else "JOKER",
                    style = TextStyle(fontFamily = Chivo, fontSize = 12.sp, letterSpacing = 0.08.em, color = if (relapse) RehabColors.DangerText else RehabColors.Text),
                )
            }
        }
    }
}

@Composable
private fun BigValue(text: String, color: Color) =
    Text(text, style = TextStyle(fontFamily = ChivoMono, fontSize = 22.sp, lineHeight = 22.sp, color = color))

@Composable
private fun SubLabel(text: String, color: Color) =
    Text(text, style = TextStyle(fontFamily = Chivo, fontSize = 9.sp, letterSpacing = 0.12.em, color = color))
