package rehab.app.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rehab.app.ui.components.AlertBanner
import rehab.app.ui.components.GaugeCard
import rehab.app.ui.components.HeaderCaption
import rehab.app.ui.components.KeyValue
import rehab.app.ui.components.KeyValueGroup
import rehab.app.ui.components.Note
import rehab.app.ui.components.PrimaryButton
import rehab.app.ui.components.AccentButton
import rehab.app.ui.components.RecordRing
import rehab.app.ui.components.RehabHeader
import rehab.app.ui.components.RehabNavBar
import rehab.app.ui.components.RehabText
import rehab.app.ui.components.RowSpec
import rehab.app.ui.components.SecondaryButton
import rehab.app.ui.components.SectionLabel
import rehab.app.ui.components.SettingsGroup
import rehab.app.ui.components.StatusPill
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors

/**
 * Galerie des composants de la tâche 2, rendue en PNG (app/build/renders) pour relecture visuelle
 * contre docs/design/screens. Aucune assertion sur les pixels ; ignoré sauf -Prehab.render=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class RenderComponents {
    @get:Rule val compose = createComposeRule()

    @Before fun gate() = assumeRendering()

    @Test fun `components home parts`() = compose.renderPng("components-home-parts") {
        Column(Modifier.width(390.dp).padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RehabHeader { StatusPill("Libre", RehabColors.Accent) }
            RecordRing(220.dp, 12.dp, RehabColors.Accent) {
                Text("RECORD", style = RehabText.ringLabel.copy(color = RehabColors.Accent))
                Text(
                    "24",
                    style = TextStyle(fontFamily = ChivoMono, fontSize = 84.sp, fontWeight = FontWeight.W300, color = RehabColors.Text),
                )
                Text("JOURS", style = RehabText.ringUnit)
            }
            RecordRing(180.dp, 12.dp, RehabColors.Accent, fraction = 12f / 23f, trackColor = RehabColors.Panel) {
                Text(
                    "12",
                    style = TextStyle(fontFamily = ChivoMono, fontSize = 48.sp, fontWeight = FontWeight.W300, color = RehabColors.Text),
                )
                Text("JOURS", style = RehabText.ringUnit)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GaugeCard("Fenêtre 30 min", "3 / 5 min", 0.6f, false)
                GaugeCard("Fenêtre 6 h", "30 / 30 min", 1f, true)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                AlertBanner("Service d'accessibilité désactivé — aucun blocage actif", "Activer") {}
            }
            RehabNavBar(listOf("Accueil", "Réglages", "Journal", "Debug"), 0) {}
        }
    }

    // IMPORTANT 1 (revue finale) : « RÉGLAGES » (le plus long des libellés) ne doit pas être coupé à
    // 130 % de police système. Vérifié en relisant le PNG.
    @Test fun `components navbar 130`() = compose.renderPng("components-navbar-130") {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
            Column(Modifier.width(390.dp).padding(top = 24.dp)) {
                RehabNavBar(listOf("Accueil", "Réglages", "Journal", "Debug"), 0) {}
            }
        }
    }

    @Test fun `components settings parts`() = compose.renderPng("components-settings-parts") {
        Column(Modifier.width(390.dp).padding(top = 24.dp)) {
            RehabHeader { HeaderCaption("application immédiate") }
            SectionLabel("Plage nocturne")
            SettingsGroup(
                listOf(
                    RowSpec("Lundi", "23:00 → 07:30", onClick = {}),
                    RowSpec("Mardi", "23:00 → 07:30", onClick = {}),
                    RowSpec(
                        "Dimanche", "23:00 → 07:30",
                        lockedReason = "Nuit en cours — modifiable à partir de 07:30",
                    ),
                ),
            )
            Note("La ligne du jour décrit la nuit qui suit. Pendant une plage en cours, sa ligne est verrouillée jusqu'au lever.")
            SecondaryButton("Ajouter une fenêtre", {}, Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp))
            AccentButton("Capturer le prochain écran cible", {}, Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp))
            PrimaryButton("Quitter", {}, Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp))
            PrimaryButton(
                "Continuer", {},
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 16.dp),
                enabled = false,
            )
            KeyValueGroup(
                listOf(
                    KeyValue("Package", "com.instagram.android"),
                    KeyValue("targetId", "InstagramReels", valueColor = RehabColors.Accent),
                ),
            )
        }
    }
}
