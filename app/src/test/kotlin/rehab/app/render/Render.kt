package rehab.app.render

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import java.io.File
import org.junit.Assume
import rehab.app.ui.theme.RehabColors
import rehab.app.ui.theme.RehabTheme

/**
 * Rend un Composable en PNG dans app/build/renders/<name>.png, pour relecture visuelle contre
 * docs/design/screens. Pas d'assertion sur les pixels. Ignoré sauf avec -Prehab.render=true :
 *   ./gradlew :app:testDebugUnitTest --tests 'rehab.app.render.*' -Prehab.render=true
 */
fun ComposeContentTestRule.renderPng(name: String, content: @Composable () -> Unit) {
    setContent { RehabTheme { Box(Modifier.background(RehabColors.Bg)) { content() } } }
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    val dir = File("build/renders").apply { mkdirs() } // cwd des tests unitaires = dossier du module app
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** Ignore le test sauf activation explicite (-Prehab.render=true) : le rendu reste hors de ./gradlew test. */
fun assumeRendering() = Assume.assumeTrue("rendu désactivé (-Prehab.render=true)", System.getProperty("rehab.render") == "true")
