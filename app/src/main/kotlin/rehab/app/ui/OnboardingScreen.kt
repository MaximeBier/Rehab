package rehab.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rehab.app.AppDisplayNames
import rehab.app.service.AppStatus
import rehab.app.ui.components.HeaderCaption
import rehab.app.ui.components.Note
import rehab.app.ui.components.PrimaryButton
import rehab.app.ui.components.RehabHeader
import rehab.app.ui.components.RowSpec
import rehab.app.ui.components.SectionLabel
import rehab.app.ui.components.SettingsGroup
import rehab.app.ui.theme.RehabColors

/**
 * Mise en page de l'écran Onboarding, sans dépendance au ViewModel ni aux réglages système : reçoit
 * l'état des trois prérequis déjà lus et la liste des apps déjà chargée, au même style que
 * `SettingsContent` (mêmes composants : `RehabHeader`, `SectionLabel`, `SettingsGroup`, `Note`,
 * `PrimaryButton`). Composable pur, testable/rendable sans Room ni lecture système.
 */
@Composable
fun OnboardingContent(
    accessibility: Boolean,
    battery: Boolean,
    notifications: Boolean,
    apps: List<AppStatus>,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RehabHeader { HeaderCaption("premier lancement") }

        SectionLabel("Avant de commencer")
        SettingsGroup(
            listOf(
                RowSpec(
                    label = "Service d'accessibilité",
                    value = if (accessibility) "Actif" else "Ouvrir",
                    valueColor = if (accessibility) RehabColors.Accent else RehabColors.Warn,
                    onClick = if (accessibility) null else onOpenAccessibility,
                ),
                RowSpec(
                    label = "Optimisation batterie désactivée",
                    value = if (battery) "Actif" else "Ouvrir",
                    valueColor = if (battery) RehabColors.Accent else RehabColors.Warn,
                    onClick = if (battery) null else onOpenBattery,
                ),
                RowSpec(
                    label = "Notifications (alerte règles)",
                    value = if (notifications) "Autorisées" else "Autoriser",
                    valueColor = if (notifications) RehabColors.Accent else RehabColors.Warn,
                    onClick = if (notifications) null else onRequestNotifications,
                ),
            ) + apps.map { s ->
                val (value, tone) = OnboardingText.appValue(s)
                RowSpec(label = AppDisplayNames.of(s.packageName), value = value, valueColor = tone.color())
            },
        )
        Note("Rehab ne bloque rien tant que le service d'accessibilité n'est pas actif. Les lignes Instagram et X apparaissent après la première activation du service.")

        Spacer(Modifier.weight(1f))
        PrimaryButton("Continuer", onContinue, Modifier.fillMaxWidth().padding(20.dp), enabled = accessibility)
    }
}

/**
 * `apps` vient de `RehabViewModel.appStatuses`, une lecture de `.value` sur le `StateFlow` publié
 * par le thread "rehab-engine" (voir ce champ) : cet écran n'appelle jamais
 * `versionChecker.checkAll()` lui-même. `prerequisites` ne touche ni Room ni `policy` : lectures
 * système pures (`Settings.Secure`, `PowerManager`), donc directement dans le Composable — une
 * seule fois par composition, dans des `val` (pas un appel `Settings.Secure` par ligne). Le retour
 * au premier plan (désactivation du service pendant que Rehab est en arrière-plan, par ex.) est géré
 * par le `tick` de `RehabApp`, qui recompose cet écran et donc relit ces `val`.
 */
@Composable
fun OnboardingScreen(
    prerequisites: Prerequisites,
    vm: RehabViewModel,
    onContinue: () -> Unit,
) {
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val apps by vm.appStatuses.collectAsState()

    val accessibility = prerequisites.accessibilityEnabled()
    val battery = prerequisites.batteryOptimizationIgnored()
    val notifications = prerequisites.notificationsAllowed()

    OnboardingContent(
        accessibility = accessibility,
        battery = battery,
        notifications = notifications,
        apps = apps,
        onOpenAccessibility = { prerequisites.openAccessibilitySettings() },
        onOpenBattery = { prerequisites.openBatterySettings() },
        onRequestNotifications = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onContinue = onContinue,
    )
}
