package rehab.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * `apps` vient de `RehabViewModel.appStatuses`, une lecture de `.value` sur le `StateFlow` publié
 * par le thread "rehab-engine" (voir ce champ) : cet écran n'appelle jamais
 * `versionChecker.checkAll()` lui-même. `prerequisites` ne touche ni Room ni `policy` : lectures
 * système pures (`Settings.Secure`, `PowerManager`), donc directement dans le Composable.
 */
@Composable
fun OnboardingScreen(
    prerequisites: Prerequisites,
    vm: RehabViewModel,
    onContinue: () -> Unit,
) {
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val apps by vm.appStatuses.collectAsState()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Avant de commencer", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Step("Service d'accessibilité activé", prerequisites.accessibilityEnabled(), "Ouvrir") { prerequisites.openAccessibilitySettings() }
        Step("Rehab exclu de l'optimisation batterie", prerequisites.batteryOptimizationIgnored(), "Ouvrir") { prerequisites.openBatterySettings() }
        Step("Notifications autorisées (alerte règles)", prerequisites.notificationsAllowed(), "Autoriser") { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        apps.forEach { s ->
            val name = if (s.packageName.contains("instagram")) "Instagram" else "X"
            val label = when {
                !s.installed -> "$name non installée"
                !s.inRange -> "$name ${s.version} hors plage testée (mode dégradé)"
                else -> "$name ${s.version} reconnue"
            }
            Step(label, s.installed && s.inRange, null) {}
        }

        Spacer(Modifier.weight(1f))
        Button(onContinue, Modifier.fillMaxWidth(), enabled = prerequisites.accessibilityEnabled()) { Text("Continuer") }
    }
}

@Composable
private fun Step(label: String, done: Boolean, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text((if (done) "✅ " else "⬜ ") + label, Modifier.weight(1f).padding(top = 12.dp))
        if (!done && action != null) OutlinedButton(onAction) { Text(action) }
    }
}
