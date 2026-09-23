package rehab.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rehab.app.ui.components.Note
import rehab.app.ui.components.RehabHeader
import rehab.app.ui.components.HeaderCaption
import rehab.app.ui.components.RowSpec
import rehab.app.ui.components.SecondaryButton
import rehab.app.ui.components.SectionLabel
import rehab.app.ui.components.SettingsGroup
import rehab.domain.model.Settings
import rehab.domain.policy.GuardResult
import rehab.rules.RedirectTab
import java.time.DayOfWeek
import java.time.ZoneId

/** Éditeur ouvert par l'utilisateur (au plus un à la fois). `null` = aucun dialogue affiché. */
sealed interface Editor {
    data class Night(val day: DayOfWeek) : Editor
    data class Window(val index: Int) : Editor
    data object NewWindow : Editor
    data object Joker : Editor
    data object Relapse : Editor
    data object JokersPerDay : Editor
    data object Hold : Editor
    /** Onglet de repli de la bascule au blocage pour l'app [packageName] (v0.3.0). */
    data class Redirect(val packageName: String) : Editor
}

/**
 * Mise en page de l'écran Réglages (`docs/design/mockups/Reglages.dc.html`), sans dépendance au
 * ViewModel : reçoit les réglages, la zone et les verrous déjà chargés, et ne fait que déclencher
 * `onEdit` au clic sur une ligne modifiable (les lignes verrouillées ne sont pas cliquables, gérées
 * par `SettingsGroup`). Composable pur, testable/rendable sans Room ni coroutine (voir RenderSettings).
 */
@Composable
fun SettingsContent(
    settings: Settings,
    zone: ZoneId,
    locks: RehabViewModel.SettingsLocks,
    redirects: Map<String, RedirectTab?>,
    onEdit: (Editor) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RehabHeader { HeaderCaption("application immédiate") }

        SectionLabel("Plage nocturne")
        SettingsGroup(
            DayOfWeek.entries.map { day ->
                val w = settings.nights.getValue(day)
                val locked = day == locks.lockedNightRow
                RowSpec(
                    label = SettingsText.dayName(day),
                    value = SettingsText.nightValue(w),
                    lockedReason = if (locked) SettingsText.nightLockReason(locks.lockedNightEnd!!, zone) else null,
                    onClick = { onEdit(Editor.Night(day)) },
                )
            },
        )
        Note("La ligne du jour décrit la nuit qui suit. Pendant une plage en cours, sa ligne est verrouillée jusqu'au lever.")

        SectionLabel("Quota glissant")
        if (settings.quotaWindows.isEmpty()) {
            Note("Aucune fenêtre : pas de quota.")
        } else {
            SettingsGroup(
                settings.quotaWindows.mapIndexed { i, w ->
                    val locked = w.duration in locks.lockedWindows
                    RowSpec(
                        label = SettingsText.windowLabel(w),
                        value = SettingsText.windowValue(w),
                        // Masqué quand verrouillé : la raison du verrou prend cette place (maquette Reglages.png).
                        subtitle = if (locked) null else "Toutes cibles confondues",
                        lockedReason = if (locked) SettingsText.quotaLockReason(locks.quotaUnlockAt!!, locks.now) else null,
                        onClick = { onEdit(Editor.Window(i)) },
                    )
                },
            )
        }
        SecondaryButton(
            "Ajouter une fenêtre",
            { onEdit(Editor.NewWindow) },
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp),
        )

        SectionLabel("Déblocage")
        SettingsGroup(
            listOf(
                RowSpec("Durée d’un joker", SettingsText.minutes(settings.jokerDuration), onClick = { onEdit(Editor.Joker) }),
                RowSpec("Durée d’un relapse", SettingsText.minutes(settings.relapseDuration), onClick = { onEdit(Editor.Relapse) }),
                RowSpec("Jokers par jour", "${settings.jokersPerDay}", onClick = { onEdit(Editor.JokersPerDay) }),
                RowSpec("Durée d’appui", SettingsText.seconds(settings.holdDuration), onClick = { onEdit(Editor.Hold) }),
            ),
        )
        Note("Les verrous sont appliqués par le domaine (SettingsGuard), pas seulement par l'écran.")

        SectionLabel("Bascule au blocage")
        SettingsGroup(
            SettingsText.redirectApps.map { (pkg, label) ->
                // Jamais verrouillée : désactiver la bascule ramène l'overlay, ça ne desserre aucun verrou.
                RowSpec(
                    label = label,
                    value = SettingsText.redirectValue(redirects[pkg]),
                    valueMono = false,
                    onClick = { onEdit(Editor.Redirect(pkg)) },
                )
            },
        )
        Note("Quand le quota est atteint, Rehab ouvre cet onglet au lieu d'afficher l'écran de blocage. La nuit, l'écran de blocage s'affiche toujours.")
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Toutes les données qui exigent une lecture Room (réglages, verrous nuit/quota) sont chargées via
 * `RehabViewModel.loadSettingsScreen`/`loadLocks`, jamais dans le corps du Composable : celui-ci
 * s'exécute sur le thread principal à chaque recomposition, et la base de production n'autorise
 * pas les requêtes sur ce thread.
 *
 * Les verrous (nuit active, quota en cours) sont recalculés chaque seconde tant que l'écran est
 * visible, sur le même principe que le rafraîchissement de l'Accueil dans `MainActivity`
 * (`repeatOnLifecycle(STARTED)` + `delay(1000)`) : sans ça, un utilisateur qui reste sur l'écran
 * pendant qu'une nuit ou un quota démarre ne verrait aucun cadenas et ne comprendrait pas le refus
 * à l'enregistrement. Seuls les verrous sont rafraîchis par cette boucle, jamais `settings` : la
 * saisie en cours de l'utilisateur (dans un dialogue ouvert) n'est jamais écrasée par ce tick.
 */
@Composable
fun SettingsScreen(vm: RehabViewModel) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<Settings?>(null) }
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    var locks by remember { mutableStateOf<RehabViewModel.SettingsLocks?>(null) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var redirects by remember { mutableStateOf<Map<String, RedirectTab?>?>(null) }

    LaunchedEffect(Unit) {
        val state = vm.loadSettingsScreen()
        settings = state.settings
        zone = state.zone
        locks = state.locks
        redirects = vm.loadRedirects()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                locks = vm.loadLocks()
                delay(1000)
            }
        }
    }

    // Validation (SettingsEdits) puis, si accepté, persistance (SettingsGuard via saveSettings).
    // Un refus (nuit/quota verrouillés entre-temps) ou une entrée invalide affiche l'erreur dans le
    // dialogue, qui reste ouvert (Review Focus 5) : pas de bouton « Enregistrer » séparé, chaque
    // dialogue applique immédiatement.
    fun save(proposed: Result<Settings>, onError: (String) -> Unit) {
        proposed.onFailure { onError(it.message ?: "Valeur invalide") }.onSuccess { p ->
            scope.launch {
                when (val r = vm.saveSettings(p)) {
                    GuardResult.Accepted -> {
                        settings = vm.loadSettingsScreen().settings
                        editor = null
                    }
                    is GuardResult.Rejected -> onError(SettingsText.rejection(r, zone))
                }
            }
        }
    }

    val s = settings ?: return
    val l = locks ?: return
    val r = redirects ?: return

    SettingsContent(s, zone, l, r) { e ->
        error = null
        editor = e
    }

    when (val e = editor) {
        is Editor.Night -> {
            val current = s.nights.getValue(e.day)
            NightDialog(e.day, current, error, onDismiss = { editor = null }) { bedtime, wakeup ->
                save(Result.success(SettingsEdits.withNight(s, e.day, bedtime, wakeup))) { error = it }
            }
        }
        is Editor.Window -> {
            val current = s.quotaWindows[e.index]
            WindowDialog(
                current,
                error,
                onDismiss = { editor = null },
                onConfirm = { d, c -> save(SettingsEdits.withWindow(s, e.index, d, c)) { error = it } },
                onDelete = { save(Result.success(SettingsEdits.removeWindow(s, e.index))) { error = it } },
            )
        }
        Editor.NewWindow -> WindowDialog(
            initial = null,
            error = error,
            onDismiss = { editor = null },
            onConfirm = { d, c -> save(SettingsEdits.addWindow(s, d, c)) { error = it } },
            onDelete = null,
        )
        Editor.Joker -> NumberDialog(
            "Durée d’un joker", "min", s.jokerDuration.toMinutes().toString(), error,
            onDismiss = { editor = null },
        ) { t -> save(SettingsEdits.withJokerMinutes(s, t)) { error = it } }
        Editor.Relapse -> NumberDialog(
            "Durée d’un relapse", "min", s.relapseDuration.toMinutes().toString(), error,
            onDismiss = { editor = null },
        ) { t -> save(SettingsEdits.withRelapseMinutes(s, t)) { error = it } }
        Editor.JokersPerDay -> NumberDialog(
            "Jokers par jour", "", s.jokersPerDay.toString(), error,
            onDismiss = { editor = null },
        ) { t -> save(SettingsEdits.withJokersPerDay(s, t)) { error = it } }
        Editor.Hold -> NumberDialog(
            "Durée d’appui", "s", s.holdDuration.seconds.toString(), error,
            onDismiss = { editor = null },
        ) { t -> save(SettingsEdits.withHoldSeconds(s, t)) { error = it } }
        is Editor.Redirect -> RedirectDialog(
            appLabel = SettingsText.redirectApps.firstOrNull { it.first == e.packageName }?.second ?: e.packageName,
            current = r[e.packageName],
            onDismiss = { editor = null },
        ) { tab ->
            scope.launch {
                vm.saveRedirect(e.packageName, tab)
                redirects = vm.loadRedirects()
                editor = null
            }
        }
        null -> {}
    }
}
