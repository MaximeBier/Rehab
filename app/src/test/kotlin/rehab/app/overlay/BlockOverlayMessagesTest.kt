package rehab.app.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rehab.app.ui.theme.RehabTheme
import rehab.domain.model.BlockReason
import rehab.domain.policy.PressOutcome
import rehab.domain.policy.StreakSummary
import java.time.Instant
import java.time.ZoneId

/** Lien « Ouvrir mes messages » de l'écran de blocage (v0.7.1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class BlockOverlayMessagesTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-09-20T20:00:00Z")
    private val state = OverlayState(
        BlockReason.Quota, now.plusSeconds(1080).toEpochMilli(), "détail", StreakSummary(24, 24, 23),
        PressOutcome.Joker(1), 10_000, 5, 15, null, ZoneId.of("Europe/Paris"),
    )
    private val link = "Ouvrir mes messages"

    @Test fun `toucher le lien appelle onOpenMessages`() {
        var opened = 0
        compose.setContent {
            RehabTheme { Box(Modifier.size(390.dp, 844.dp)) { BlockOverlay(state, { now.toEpochMilli() }, {}, {}, onOpenMessages = { opened++ }) } }
        }
        compose.onNodeWithText(link).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEquals(1, opened)
    }

    @Test fun `sans lien quitter ne bouge pas`() {
        var withLink by mutableStateOf(true)
        compose.setContent {
            RehabTheme {
                Box(Modifier.size(390.dp, 844.dp)) {
                    BlockOverlay(state, { now.toEpochMilli() }, {}, {}, onOpenMessages = if (withLink) ({}) else null)
                }
            }
        }
        val withBounds = compose.onNodeWithText("QUITTER").getBoundsInRoot()
        withLink = false
        compose.waitForIdle()
        compose.onNodeWithText(link).assertDoesNotExistCompat()
        assertEquals(withBounds, compose.onNodeWithText("QUITTER").getBoundsInRoot())
    }

    @Test fun `lien pendant l appui annule l appui`() {
        var opened = 0
        var completed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RehabTheme {
                Box(Modifier.size(390.dp, 844.dp)) {
                    BlockOverlay(state, { now.toEpochMilli() }, {}, onHoldCompleted = { completed++ }, onOpenMessages = { opened++ })
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Maintenir pour un joker").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText(link).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(12_000)
        assertEquals(1, opened)
        assertEquals(0, completed)
    }

    /**
     * Doigt posé sur le lien (sans le relever) : [MessagesSlot] signale le contact tout de suite, pour annuler un appui en
     * cours avant la fin — un relapse est irréversible, on n'attend pas le relâcher du clic.
     */
    @Test fun `poser le doigt sur le lien signale le contact sans ouvrir`() {
        var pressed = 0
        var opened = 0
        compose.setContent { RehabTheme { MessagesSlot(onClick = { opened++ }, onPressStart = { pressed++ }) } }
        compose.onNodeWithText(link).performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(1, pressed)
        assertEquals(0, opened)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
}
