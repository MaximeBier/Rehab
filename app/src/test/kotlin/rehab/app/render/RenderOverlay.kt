package rehab.app.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rehab.app.overlay.BlockOverlay
import rehab.app.overlay.HoldButton
import rehab.app.overlay.HoldKind
import rehab.app.overlay.OverlayState
import rehab.app.overlay.OverlayText
import rehab.app.ui.components.RehabText
import rehab.app.ui.theme.RehabColors
import rehab.domain.model.BlockReason
import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.PressOutcome
import rehab.domain.policy.StreakSummary
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Rendus de l'overlay de blocage (tâche 7), en PNG (app/build/renders) pour relecture visuelle contre
 * docs/design/screens/Overlay-*.png et Etats-Bouton.png. Aucune assertion sur les pixels ; ignoré sauf
 * -Prehab.render=true. La bande « barre de navigation système · non couverte » des maquettes est une
 * annotation, pas de l'UI : elle n'est pas dessinée.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class RenderOverlay {
    @get:Rule val compose = createComposeRule()

    @Before fun gate() = assumeRendering()

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 9, d, h, m, 0, 0, zone).toInstant()

    private val record = StreakSummary(24, 24, 23)
    private val night = NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30))

    private fun state(reason: BlockReason, unlock: Instant, detail: String, outcome: PressOutcome, streak: StreakSummary = record) =
        OverlayState(reason, unlock.toEpochMilli(), detail, streak, outcome, 10_000, 5, 15, null, zone)

    private fun overlay(name: String, state: OverlayState, now: Instant) = compose.renderPng(name) {
        Box(Modifier.size(390.dp, 844.dp)) {
            BlockOverlay(state, nowMillis = { now.toEpochMilli() }, onQuit = {}, onHoldCompleted = {})
        }
    }

    @Test fun `overlay quota`() = overlay(
        "overlay-quota",
        state(BlockReason.Quota, at(20, 22, 18), OverlayText.quotaDetail(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))), PressOutcome.Joker(1)),
        at(20, 22, 0),
    )

    @Test fun `overlay nuit`() = overlay(
        "overlay-nuit",
        state(BlockReason.Night, at(21, 7, 30), OverlayText.nightDetail(DayOfWeek.SUNDAY, night), PressOutcome.Relapse(24)),
        at(20, 23, 30),
    )

    @Test fun `overlay relapse`() = overlay(
        "overlay-relapse",
        state(BlockReason.Quota, at(20, 22, 18), OverlayText.jokersExhausted(2), PressOutcome.Relapse(24)),
        at(20, 22, 0),
    )

    // IMPORTANT 1 (revue finale) : la légende de l'overlay-nuit tient sur deux lignes complètes
    // (« ... et ton streak de 24 jours tombe ») ; à 130 % de police système, la 2ᵉ ligne était coupée
    // avant que la boîte ne soit calculée en sp (voir BlockOverlay). Vérifié en relisant le PNG.
    @Test fun `overlay nuit 130`() = compose.renderPng("overlay-nuit-130") {
        val now = at(20, 23, 30)
        Box(Modifier.size(390.dp, 844.dp)) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                BlockOverlay(
                    state(BlockReason.Night, at(21, 7, 30), OverlayText.nightDetail(DayOfWeek.SUNDAY, night), PressOutcome.Relapse(24)),
                    nowMillis = { now.toEpochMilli() },
                    onQuit = {},
                    onHoldCompleted = {},
                )
            }
        }
    }

    @Test fun `overlay hors record`() = overlay(
        "overlay-hors-record",
        state(BlockReason.Night, at(21, 7, 30), OverlayText.nightDetail(DayOfWeek.SUNDAY, night), PressOutcome.Relapse(12), StreakSummary(12, 23, 23)),
        at(20, 23, 30),
    )

    @Test fun `overlay etats bouton`() = compose.renderPng("overlay-etats-bouton") {
        Column(Modifier.width(390.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(HoldKind.Joker to "+5", HoldKind.Relapse to "+15").forEach { (kind, doneValue) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Cell("${kind.name} · repos") { HoldButton(kind, 0f, 10, false, doneValue, 1f) {} }
                    Cell("${kind.name} · appui") { HoldButton(kind, 0.6f, 4, false, doneValue, 1f) {} }
                    Cell("${kind.name} · terminé") { HoldButton(kind, 1f, 0, true, doneValue, 1f) {} }
                }
            }
        }
    }

    @Composable
    private fun Cell(label: String, button: @Composable () -> Unit) {
        Column(
            Modifier.width(112.dp).background(RehabColors.Panel, RoundedCornerShape(20.dp)).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            button()
            Text(label, style = RehabText.small11)
        }
    }
}
