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
import rehab.app.service.CaptureNames
import rehab.app.service.LastDetection
import rehab.app.service.RedirectAttempt
import rehab.rules.RedirectTab
import rehab.app.ui.CaptureFile
import rehab.app.ui.DebugContent
import rehab.app.ui.DebugText
import rehab.domain.model.Decision
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.SlidingQuota
import java.io.File
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Rendu de `DebugContent` (tâche 6), en PNG (app/build/renders) pour relecture visuelle contre
 * docs/design/screens/Debug.png. Aucune assertion sur les pixels ; ignoré sauf -Prehab.render=true.
 * La barre de navigation est dessinée par `RehabApp`, pas par `DebugContent` (même remarque que
 * RenderHome/RenderJournal), et la fenêtre simulée fait 390×1000 dp comme `Debug.dc.html`.
 *
 * Écart volontaire avec la maquette : les noms de capture y omettent les secondes
 * (« ig_reels_2026-09-20T21-31.json ») pour tenir sur la largeur affichée, alors que
 * `CaptureNames.fileName` (tâche 6) les inclut toujours (« …T21-31-05.json »). Ce rendu utilise le
 * vrai format produit par le code, pas la simplification de la maquette.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h1000dp-xxhdpi")
class RenderDebug {
    @get:Rule val compose = createComposeRule()

    @Before fun gate() = assumeRendering()

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 9, d, h, m, s, 0, zone).toInstant()

    @Test fun debug() = compose.renderPng("debug") {
        val now = at(20, 21, 31, 8)
        val detectedAt = at(20, 21, 31, 5)
        val u30 = SlidingQuota.WindowUsage(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5)), Duration.ofMinutes(3))
        val u6h = SlidingQuota.WindowUsage(QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30)), Duration.ofMinutes(12))
        val decisionSummary = DebugText.decision(Decision.Allow, null, listOf(u30, u6h), zone)

        val last = LastDetection(
            packageName = "com.instagram.android",
            appVersion = "412.0.0.35.104",
            target = "InstagramReels",
            screenId = "instagram.reels",
            unknownScreen = false,
            degraded = false,
            atMillis = detectedAt.toEpochMilli(),
            degradedReason = null,
        )
        val captures = listOf(
            CaptureFile(File("ig_reels.json"), CaptureNames.fileName("com.instagram.android", "instagram.reels", at(20, 21, 31, 5).toEpochMilli(), zone), 38 * 1024L),
            CaptureFile(File("ig_feed.json"), CaptureNames.fileName("com.instagram.android", "instagram.home", at(20, 18, 2).toEpochMilli(), zone), 52 * 1024L),
            CaptureFile(File("x_home.json"), CaptureNames.fileName("com.twitter.android", "twitter.home", at(19, 12, 11).toEpochMilli(), zone), 41 * 1024L),
        )

        Box(Modifier.size(390.dp, 1000.dp)) {
            DebugContent(
                delayText = "5",
                onDelayChange = {},
                pendingAt = null,
                nowMillis = now.toEpochMilli(),
                last = last,
                redirect = RedirectAttempt("com.instagram.android", RedirectTab.Messages, at(20, 21, 31, 5).toEpochMilli(), failed = false),
                decisionSummary = decisionSummary,
                captures = captures,
                serviceAvailable = true,
                onCapture = {},
                onExport = {},
                onOverlay = {},
                onShareOne = {},
            )
        }
    }
}
