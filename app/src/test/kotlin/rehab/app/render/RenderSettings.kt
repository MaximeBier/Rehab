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
import rehab.app.ui.SettingsContent
import rehab.domain.model.Settings
import rehab.rules.RedirectTab
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Rendu de `SettingsContent` (tâche 4), en PNG (app/build/renders) pour relecture visuelle contre
 * docs/design/screens/Reglages.png. Aucune assertion sur les pixels ; ignoré sauf
 * -Prehab.render=true. La barre de navigation est dessinée par `RehabApp`, pas par
 * `SettingsContent` : elle n'apparaît donc pas dans ce rendu (voir RenderHome, même remarque).
 */
// Fenêtre simulée plus haute que les autres rendus (h1240dp au lieu de h844dp) : la liste des
// réglages dépasse la hauteur d'un écran de téléphone (comme la maquette Reglages.dc.html, 1240 px
// de haut) et `captureToImage()` ne capture que la fenêtre simulée, pas tout le contenu défilable.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h1240dp-xxhdpi")
class RenderSettings {
    @get:Rule val compose = createComposeRule()

    @Before fun gate() = assumeRendering()

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 9, 20, h, m, 0, 0, zone).toInstant()

    @Test fun `reglages verrous`() = compose.renderPng("reglages-verrous") {
        val now = at(22, 0)
        val locks = RehabViewModel.SettingsLocks(
            now = now,
            // Dimanche : nuit en cours, modifiable à partir de 07:30 (nuit qui se termine le lendemain).
            lockedNightRow = java.time.DayOfWeek.SUNDAY,
            lockedNightEnd = at(7, 30).plus(Duration.ofDays(1)),
            // Quota 30 min en dépassement, déblocage dans 18 min.
            quotaUnlockAt = now.plus(Duration.ofMinutes(18)),
            lockedWindows = setOf(Duration.ofMinutes(30)),
        )
        val redirects = mapOf("com.instagram.android" to RedirectTab.Messages, "com.twitter.android" to null)
        val statsBefore = emptyMap<String, RehabViewModel.StatsBeforeRow>()
        Box(Modifier.size(390.dp, 1240.dp)) {
            SettingsContent(Settings.DEFAULT, zone, locks, redirects, statsBefore) {}
        }
    }

    /**
     * v0.3.0 : section « Bascule au blocage » en bas de l'écran. Fenêtre plus haute (h1560dp) pour que la
     * section, sous « Déblocage », tienne dans la capture ; pas de verrou actif ici (hors plage nocturne).
     * v0.4.0 : la section « Avant Rehab » (Instagram saisi, X déduit de l'historique Android) suit — fenêtre
     * encore agrandie (h1760dp) pour que les deux sections tiennent dans la capture.
     */
    @Config(sdk = [34], qualifiers = "w390dp-h1760dp-xxhdpi")
    @Test fun `reglages bascule`() = compose.renderPng("reglages-bascule") {
        val now = at(15, 0)
        val locks = RehabViewModel.SettingsLocks(now, null, null, null, emptySet())
        val redirects = mapOf("com.instagram.android" to RedirectTab.Messages, "com.twitter.android" to RedirectTab.Search)
        val statsBefore = mapOf(
            "com.instagram.android" to RehabViewModel.StatsBeforeRow(Duration.ofMinutes(45), null),
            "com.twitter.android" to RehabViewModel.StatsBeforeRow(null, Duration.ofMinutes(38)),
        )
        Box(Modifier.size(390.dp, 1760.dp)) {
            SettingsContent(Settings.DEFAULT, zone, locks, redirects, statsBefore) {}
        }
    }
}
