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
import rehab.app.service.AppStatus
import rehab.app.ui.OnboardingContent

/**
 * Rendu de `OnboardingContent` (tâche 8), en PNG (app/build/renders) pour relecture visuelle contre
 * docs/design/screens/Reglages.png (mêmes composants, DESIGN §4). Aucune assertion sur les pixels ;
 * ignoré sauf -Prehab.render=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class RenderOnboarding {
    @get:Rule val compose = createComposeRule()

    @Before fun gate() = assumeRendering()

    @Test fun `onboarding a faire`() = compose.renderPng("onboarding-a-faire") {
        Box(Modifier.size(390.dp, 844.dp)) {
            OnboardingContent(
                accessibility = false,
                battery = false,
                notifications = false,
                apps = emptyList(),
                onOpenAccessibility = {},
                onOpenBattery = {},
                onRequestNotifications = {},
                onContinue = {},
            )
        }
    }

    @Test fun `onboarding pret`() = compose.renderPng("onboarding-pret") {
        Box(Modifier.size(390.dp, 844.dp)) {
            OnboardingContent(
                accessibility = true,
                battery = true,
                notifications = true,
                apps = listOf(
                    AppStatus("com.instagram.android", installed = true, version = "412.0.0.35.104", inRange = true),
                    AppStatus("com.twitter.android", installed = true, version = "999.0.0", inRange = false),
                ),
                onOpenAccessibility = {},
                onOpenBattery = {},
                onRequestNotifications = {},
                onContinue = {},
            )
        }
    }
}
