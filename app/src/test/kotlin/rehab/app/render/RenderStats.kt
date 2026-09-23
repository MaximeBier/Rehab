package rehab.app.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rehab.app.ui.RehabViewModel
import rehab.app.ui.StatsContent
import rehab.app.ui.StatsText
import java.time.Duration

/**
 * Rendu de `StatsContent` (v0.4.0, écran Stats), en PNG (app/build/renders). Aucune assertion sur
 * les pixels ; ignoré sauf -Prehab.render=true. La barre de navigation est dessinée par `RehabApp`,
 * pas par `StatsContent` : elle n'apparaît donc pas dans ce rendu (même remarque que RenderJournal).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h1240dp-xxhdpi")
class RenderStats {
    @get:Rule val compose = createComposeRule()

    @Before fun gate() = assumeRendering()

    @Test fun stats() = compose.renderPng("stats") {
        val instagram = StatsText.card(
            "Instagram", Duration.ofMinutes(45), "Android", Duration.ofMinutes(12), Duration.ofMinutes(9), hasPermission = true,
        )
        val x = StatsText.card(
            "X", Duration.ofMinutes(38), "saisi", Duration.ofMinutes(41), Duration.ofMinutes(30), hasPermission = true,
        )
        val total = StatsText.card(
            "Total", Duration.ofMinutes(83), null, Duration.ofMinutes(53), Duration.ofMinutes(39), hasPermission = true,
        )
        val state = RehabViewModel.StatsUiState(hasPermission = true, apps = listOf(instagram, x), total = total)
        Box(Modifier.size(390.dp, 1240.dp)) { StatsContent(state) {} }
    }

    @Test fun `stats sans acces`() = compose.renderPng("stats-sans-acces") {
        val instagram = StatsText.card("Instagram", null, null, null, Duration.ofMinutes(9), hasPermission = false)
        val x = StatsText.card("X", Duration.ofMinutes(38), "saisi", null, Duration.ofMinutes(30), hasPermission = false)
        val total = StatsText.card("Total", null, null, null, Duration.ofMinutes(39), hasPermission = false)
        val state = RehabViewModel.StatsUiState(hasPermission = false, apps = listOf(instagram, x), total = total)
        Box(Modifier.size(390.dp, 1240.dp)) { StatsContent(state) {} }
    }
}
