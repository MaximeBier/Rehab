package rehab.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import rehab.app.ui.HomeText
import rehab.app.ui.components.PrimaryButton
import rehab.app.ui.components.RecordRing
import rehab.app.ui.components.RehabText
import rehab.app.ui.components.withMonoNumbers
import rehab.app.ui.theme.Chivo
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors
import rehab.domain.policy.PressOutcome
import kotlin.math.ceil

/**
 * Overlay de blocage (DESIGN §5 ; maquettes `Overlay-Quota`, `Overlay-Nuit`, `Overlay-Relapse`,
 * `Overlay-HorsRecord`, `Overlay-Proto`). Même fond et même structure quelle que soit la condition ;
 * seuls changent la variante du bouton et le texte d'annonce. L'appui long est animé image par image :
 * `p` ∈ [0, 1] pilote tous les effets relapse ([RelapseFx]) ; le joker n'a que la jauge et le décompte.
 */
@Composable
fun BlockOverlay(state: OverlayState, nowMillis: () -> Long, onQuit: () -> Unit, onHoldCompleted: () -> Unit) {
    // Titre statique (DESIGN §6.1) : figé à l'apparition du blocage, pas de compte à rebours.
    val shownAt = remember(state.reason, state.unlockAtMillis) { nowMillis() }
    val relapse = state.outcome is PressOutcome.Relapse
    val completed by rememberUpdatedState(onHoldCompleted)
    val holdMillis by rememberUpdatedState(state.holdMillis)
    var pressing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var p by remember { mutableFloatStateOf(0f) }
    var pulsePhase by remember { mutableFloatStateOf(0f) }
    var shakePhase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pressing) {
        if (!pressing) { if (!done) p = 0f; return@LaunchedEffect }   // relâché : retour instantané à 0
        val start = withFrameMillis { it }
        var last = start
        while (pressing && !done) {
            val t = withFrameMillis { it }
            val dt = (t - last) / 1000f
            last = t
            p = ((t - start).toFloat() / holdMillis.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (relapse) {
                pulsePhase = (pulsePhase + dt / RelapseFx.pulsePeriodSec(p)) % 1f
                if (p > 0.6f) shakePhase = (shakePhase + dt / RelapseFx.shakePeriodSec(p)) % 1f
            }
            if (p >= 1f) {
                done = true
                completed()
            }
        }
    }

    val fx = if (relapse) (if (done) 1f else p) else 0f     // joker : ni fond, ni tremblement, ni pulsation
    val holding = pressing && !done
    val bg = if (relapse && (holding || done)) RelapseFx.background(fx) else RehabColors.Bg
    val secondsLeft = ceil((1f - p) * state.holdMillis / 1000f).toInt()
    val s = state.streak

    Column(
        Modifier.fillMaxSize().background(bg)
            .graphicsLayer { translationX = if (holding) RelapseFx.shakeDp(fx, shakePhase).dp.toPx() else 0f }
            .then(if (state.navBarTop == null) Modifier.navigationBarsPadding() else Modifier)
            .padding(top = 64.dp),
    ) {
        Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("REHAB · BLOCAGE", style = RehabText.caps12.copy(letterSpacing = 0.16.em))
            Text(
                OverlayText.title(state, shownAt),
                style = TextStyle(fontFamily = Chivo, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.W500, color = RehabColors.Text),
            )
            Text(state.detail, style = TextStyle(fontFamily = Chivo, fontSize = 14.sp, color = RehabColors.Muted))
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val days = when {
                done && relapse -> 0
                relapse -> RelapseFx.daysShown(s.current, fx)
                else -> s.current
            }
            val daysColor = if (relapse && fx > 0.5f) RehabColors.Danger else RehabColors.Text
            val daysStyle = TextStyle(fontFamily = ChivoMono, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.W300, color = daysColor)
            // Hors record : anneau plein `ring-off` et « SÉRIE » (maquette Overlay-HorsRecord) ; l'arc partiel est propre à l'Accueil.
            if (s.inRecord) {
                RecordRing(176.dp, 10.dp, RehabColors.Accent, ringAlpha = 1f - fx, overlayArc = fx) {
                    Text("RECORD", style = RehabText.ringLabel.copy(color = RehabColors.Accent.copy(alpha = 1f - fx)))
                    Spacer(Modifier.height(3.dp))
                    Text("$days", style = daysStyle)
                    Spacer(Modifier.height(3.dp))
                    Text(HomeText.unit(days), style = RehabText.ringUnit)
                }
            } else {
                RecordRing(176.dp, 10.dp, RehabColors.RingOff, overlayArc = fx) {
                    Text("SÉRIE", style = RehabText.ringLabel.copy(color = RehabColors.Muted))
                    Spacer(Modifier.height(3.dp))
                    Text("$days", style = daysStyle)
                    Spacer(Modifier.height(3.dp))
                    Text(HomeText.unit(days), style = RehabText.ringUnit)
                }
            }
            val line = when {
                done && relapse -> OverlayText.doneStreakLine(s)
                holding && relapse && fx > 0.5f -> "Tu es en train de le perdre."
                else -> OverlayText.streakLine(s)
            }
            Text(
                withMonoNumbers(line, RehabColors.Text),
                style = TextStyle(fontFamily = Chivo, fontSize = 13.sp, color = RehabColors.Muted, textAlign = TextAlign.Center),
                modifier = Modifier.defaultMinSize(minHeight = 20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HoldButton(
                kind = if (relapse) HoldKind.Relapse else HoldKind.Joker,
                progress = if (done) 1f else p,
                secondsLeft = secondsLeft,
                done = done,
                doneValue = "+" + (if (relapse) state.relapseMinutes else state.jokerMinutes),
                // Comme Overlay-Proto : l'échelle atteint 1.18 et y reste dans l'état terminé (sans pulsation).
                scale = when {
                    relapse && holding -> RelapseFx.buttonScale(fx, pulsePhase)
                    relapse && done -> RelapseFx.buttonScale(1f, 0f)
                    else -> 1f
                },
                onPressChange = { if (!done) pressing = it },
            )
            // Boîte de hauteur fixe (40 dp) : rien ne bouge quand le texte change (DESIGN §5.5).
            val k = if (relapse) RelapseFx.captionScale(fx) else 1f
            Box(
                Modifier.height(40.dp).widthIn(max = 300.dp).graphicsLayer { scaleX = k; scaleY = k },
                contentAlignment = Alignment.Center,
            ) {
                val color = if (relapse) RehabColors.Danger else RehabColors.Text
                val style = TextStyle(
                    fontFamily = Chivo, fontSize = 13.sp, lineHeight = 19.sp, color = color, textAlign = TextAlign.Center,
                    fontWeight = if (relapse && fx > 0.5f) FontWeight.W700 else FontWeight.W400,
                )
                when {
                    done -> Text(OverlayText.doneCaption(state), style = style)
                    holding -> Text(OverlayText.holdingCaption(state, secondsLeft), style = style)
                    else -> {
                        val c = OverlayText.caption(state)
                        Text(
                            buildAnnotatedString {
                                append(c.prefix)
                                withStyle(SpanStyle(fontWeight = FontWeight.W700, color = if (relapse) RehabColors.Danger else RehabColors.Accent)) { append(c.emphasis) }
                                append(c.suffix)
                            },
                            style = style,
                        )
                    }
                }
            }
        }
        PrimaryButton("Quitter", onQuit, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 16.dp))
    }
}
