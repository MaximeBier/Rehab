package rehab.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import rehab.app.ui.HomeText
import rehab.app.ui.components.PrimaryButton
import rehab.app.ui.components.RecordRing
import rehab.app.ui.components.RehabText
import rehab.app.ui.components.withMonoNumbers
import rehab.app.ui.theme.Chivo
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors
import rehab.domain.policy.PressOutcome
import rehab.domain.policy.StreakSummary

/**
 * Overlay de blocage (maquettes `Overlay-Quota`, `Overlay-Nuit`, `Overlay-Relapse`,
 * `Overlay-HorsRecord`, `Overlay-Proto`). Même fond et même structure quelle que soit la condition ;
 * seuls changent la variante du bouton et le texte d'annonce. L'appui long est animé image par image :
 * `p` ∈ [0, 1] pilote tous les effets relapse ([RelapseFx]) ; le joker n'a que la jauge et le décompte.
 */
@Composable
fun BlockOverlay(
    state: OverlayState,
    nowMillis: () -> Long,
    onQuit: () -> Unit,
    onHoldCompleted: () -> Unit,
    /** Lien « Ouvrir mes messages » (v0.7.1) ; `null` : pas d'ouverture fiable pour l'app, emplacement laissé vide. */
    onOpenMessages: (() -> Unit)? = null,
) {
    // Titre statique : figé à l'apparition du blocage, pas de compte à rebours.
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

    val days = when {
        done && relapse -> 0
        relapse -> RelapseFx.daysShown(s.current, fx)
        else -> s.current
    }
    val line = when {
        done && relapse -> OverlayText.doneStreakLine(s)
        holding && relapse && fx > 0.5f -> "Tu es en train de le perdre."
        else -> OverlayText.streakLine(s)
    }
    val actions: @Composable () -> Unit = {
        OverlayActions(
            state, relapse, p, done, holding, fx, pulsePhase, secondsLeft,
            onPressChange = { if (!done) pressing = it }, onQuit = onQuit,
            // Toucher le lien pendant un appui l'annule sans effet (p revient à 0, completed() n'est jamais appelé), puis ouvre.
            // L'annulation part dès le contact du doigt (onMessagesPress), pas au relâcher : un relapse est irréversible.
            onOpenMessages = onOpenMessages?.let { open -> { if (!done) pressing = false; open() } },
            onMessagesPress = { if (!done) pressing = false },
        )
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(bg)
            .graphicsLayer { translationX = if (holding) RelapseFx.shakeDp(fx, shakePhase).dp.toPx() else 0f }
            .then(if (state.navBarTop == null) Modifier.navigationBarsPadding() else Modifier),
    ) {
        if (maxWidth > maxHeight) {
            // Paysage (v0.6.2, sans défilement depuis v0.7.1) : deux colonnes. À gauche, l'anneau prend la place restante
            // sous l'en-tête (voir AdaptiveStreak) ; à droite, les actions gardent leur taille, centrées.
            val vertical = if (maxHeight < 340.dp) 8.dp else 24.dp
            Row(
                Modifier.fillMaxSize().displayCutoutPadding().padding(horizontal = 28.dp, vertical = vertical),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    OverlayHeader(state, shownAt)
                    AdaptiveStreak(
                        s, days, fx, relapse, line, maxRing = 128.dp, minRing = 80.dp, maxStroke = 8.dp, maxDaysFont = 48.sp,
                        gap = 20.dp, modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { actions() }
            }
        } else {
            // Portrait (v0.7.1) : jamais de défilement. En-tête et actions gardent leur taille ; l'anneau prend la place
            // restante entre les deux (fenêtre bornée par la barre d'onglets, police à 130 %, écran partagé).
            Column(Modifier.fillMaxSize().padding(top = if (maxHeight < 640.dp) 24.dp else 64.dp)) {
                Column(Modifier.padding(horizontal = 28.dp)) { OverlayHeader(state, shownAt) }
                AdaptiveStreak(
                    s, days, fx, relapse, line, maxRing = 176.dp, minRing = 104.dp, maxStroke = 10.dp, maxDaysFont = 64.sp,
                    gap = 40.dp, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
                )
                actions()
            }
        }
    }
}

/**
 * Anneau de série dans la place que lui laissent l'en-tête et les actions (v0.7.1 : l'écran de blocage ne défile
 * jamais). 16 dp restent libres en dessous. Ordre de repli quand la place manque : l'écart au-dessus se réduit à 12 dp, puis l'anneau rétrécit
 * jusqu'à [minRing] (trait et chiffres à l'échelle), puis la ligne de série disparaît, puis l'anneau lui-même.
 */
@Composable
private fun AdaptiveStreak(
    s: StreakSummary, days: Int, fx: Float, relapse: Boolean, line: String,
    maxRing: Dp, minRing: Dp, maxStroke: Dp, maxDaysFont: TextUnit, gap: Dp, modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val lineHeight = remember(line, maxWidth, density) {
            val measured = measurer.measure(
                withMonoNumbers(line, RehabColors.Text), streakLineStyle,
                constraints = Constraints(maxWidth = with(density) { maxWidth.roundToPx() }),
            )
            maxOf(with(density) { measured.size.height.toDp() }, 20.dp)
        }
        // 16 dp gardés sous la série : l'anneau ne vient jamais coller au bouton d'appui.
        fun ringFor(top: Dp, withLine: Boolean) = maxHeight - top - 16.dp - (if (withLine) 12.dp + lineHeight else 0.dp)
        val choice = listOf(gap to true, 12.dp to true, 12.dp to false)
            .firstOrNull { (top, withLine) -> ringFor(top, withLine) >= minRing && (top == gap || ringFor(gap, withLine) < maxRing) }
        if (choice != null) {
            val (top, withLine) = choice
            val ring = ringFor(top, withLine).coerceAtMost(maxRing)
            val k = ring / maxRing
            Column(Modifier.padding(top = top)) {
                OverlayStreak(s, days, fx, relapse, line, ringSize = ring, ringStroke = maxStroke * k, daysFontSize = maxDaysFont * k, showLine = withLine)
            }
        }
    }
}

private val streakLineStyle = TextStyle(fontFamily = Chivo, fontSize = 13.sp, color = RehabColors.Muted, textAlign = TextAlign.Center)

/** En-tête : surtitre, titre statique et détail de la condition de blocage. */
@Composable
private fun OverlayHeader(state: OverlayState, shownAt: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("REHAB · BLOCAGE", style = RehabText.caps12.copy(letterSpacing = 0.16.em))
        Text(
            OverlayText.title(state, shownAt),
            style = TextStyle(fontFamily = Chivo, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.W500, color = RehabColors.Text),
        )
        Text(state.detail, style = TextStyle(fontFamily = Chivo, fontSize = 14.sp, color = RehabColors.Muted))
    }
}

/** Anneau de série et ligne de série ; la taille de l'anneau est choisie par [AdaptiveStreak]. */
@Composable
private fun OverlayStreak(
    s: StreakSummary, days: Int, fx: Float, relapse: Boolean, line: String,
    ringSize: Dp, ringStroke: Dp, daysFontSize: TextUnit, showLine: Boolean = true,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val daysColor = if (relapse && fx > 0.5f) RehabColors.Danger else RehabColors.Text
        val daysStyle = TextStyle(fontFamily = ChivoMono, fontSize = daysFontSize, lineHeight = daysFontSize, fontWeight = FontWeight.W300, color = daysColor)
        // Hors record : anneau plein `ring-off` et « SÉRIE » (maquette Overlay-HorsRecord) ; l'arc partiel est propre à l'Accueil.
        if (s.inRecord) {
            RecordRing(ringSize, ringStroke, RehabColors.Accent, ringAlpha = 1f - fx, overlayArc = fx) {
                Text("RECORD", style = RehabText.ringLabel.copy(color = RehabColors.Accent.copy(alpha = 1f - fx)))
                Spacer(Modifier.height(3.dp))
                Text("$days", style = daysStyle)
                Spacer(Modifier.height(3.dp))
                Text(HomeText.unit(days), style = RehabText.ringUnit)
            }
        } else {
            RecordRing(ringSize, ringStroke, RehabColors.RingOff, overlayArc = fx) {
                Text("SÉRIE", style = RehabText.ringLabel.copy(color = RehabColors.Muted))
                Spacer(Modifier.height(3.dp))
                Text("$days", style = daysStyle)
                Spacer(Modifier.height(3.dp))
                Text(HomeText.unit(days), style = RehabText.ringUnit)
            }
        }
        if (showLine) Text(
            withMonoNumbers(line, RehabColors.Text),
            style = streakLineStyle,
            modifier = Modifier.defaultMinSize(minHeight = 20.dp),
        )
    }
}

/** Bouton d'appui long, sa légende de hauteur fixe et « Quitter ». */
@Composable
private fun OverlayActions(
    state: OverlayState, relapse: Boolean, p: Float, done: Boolean, holding: Boolean, fx: Float,
    pulsePhase: Float, secondsLeft: Int, onPressChange: (Boolean) -> Unit, onQuit: () -> Unit,
    onOpenMessages: (() -> Unit)?,
    onMessagesPress: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
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
                onPressChange = onPressChange,
            )
            // Boîte de hauteur fixe : rien ne bouge quand le texte change. Mesurée (pas
            // calculée à la main en sp) : à 130 % de police système, une formule sp * dp naïve restait
            // trop courte de près d'une ligne (métriques réelles de Chivo à cette taille, pas seulement
            // une histoire d'arrondi) — `rememberTextMeasurer` mesure exactement ce que `Text` va rendre,
            // donc les deux ne peuvent pas diverger.
            val k = if (relapse) RelapseFx.captionScale(fx) else 1f
            val color = if (relapse) RehabColors.Danger else RehabColors.Text
            val style = TextStyle(
                fontFamily = Chivo, fontSize = 13.sp, lineHeight = 19.sp, color = color, textAlign = TextAlign.Center,
                fontWeight = if (relapse && fx > 0.5f) FontWeight.W700 else FontWeight.W400,
            )
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val captionHeight = remember(density, style.fontSize, style.lineHeight, style.fontWeight) {
                val maxWidthPx = with(density) { 300.dp.roundToPx() }
                val measured = textMeasurer.measure(
                    text = "x\nx", // référence deux lignes : la légende tient toujours sur 1 ou 2 lignes.
                    style = style,
                    constraints = Constraints(maxWidth = maxWidthPx),
                )
                with(density) { measured.size.height.toDp() }
            }
            Box(
                Modifier.height(captionHeight).widthIn(max = 300.dp).graphicsLayer { scaleX = k; scaleY = k },
                contentAlignment = Alignment.Center,
            ) {
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
        // v0.7.1 : 10 dp, emplacement de 44 dp (réservé même sans lien), 8 dp jusqu'à « Quitter » (au lieu de 26).
        MessagesSlot(onOpenMessages, Modifier.padding(top = 10.dp), onPressStart = onMessagesPress)
        PrimaryButton("Quitter", onQuit, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp))
    }
}
