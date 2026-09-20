package rehab.app.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import rehab.app.RehabApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as RehabApp).graph
        setContent {
            MaterialTheme {
                val vm: RehabViewModel = viewModel(factory = viewModelFactory { initializer { RehabViewModel(graph) } })
                RehabApp(vm)
            }
        }
    }
}

@Composable
fun RehabApp(vm: RehabViewModel) {
    val context = LocalContext.current
    val prerequisites = remember { Prerequisites(context) }
    val prefs = remember { context.getSharedPreferences("rehab_ui", Context.MODE_PRIVATE) }
    var onboardingDone by remember { mutableStateOf(prefs.getBoolean("onboarding_done", false)) }

    // Ré-évalue les prérequis (accessibilité surtout) à chaque retour au premier plan : ce sont
    // des réglages système, modifiables par l'utilisateur pendant que Rehab est en arrière-plan
    // (ex. il désactive le service d'accessibilité puis revient). `tick` ne sert qu'à déclencher
    // une recomposition ; `prerequisites.accessibilityEnabled()` est relu à chaque appel, jamais
    // mis en cache.
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // `remember(tick)` : sans lui, cette ligne s'exécutait à chaque recomposition de RehabApp, y
    // compris celles déclenchées par `home` (une par seconde via vm.refreshNow(), voir plus bas) —
    // soit une requête `Settings.Secure` sur le thread principal chaque seconde (IMPORTANT/mineur
    // de la revue finale). `remember(tick)` ne relit ce prérequis qu'au retour au premier plan.
    val accessibilityEnabled = remember(tick) { prerequisites.accessibilityEnabled() }
    if (!onboardingDone || !accessibilityEnabled) {
        OnboardingScreen(prerequisites, vm) {
            prefs.edit().putBoolean("onboarding_done", true).apply()
            onboardingDone = true
        }
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.Accueil) }
    val home by vm.home.collectAsState()

    // Rafraîchissement lié au cycle de vie : ne tourne (une lecture Room par seconde) que tant
    // que l'écran est visible (STARTED), pas en continu écran éteint ou app en arrière-plan.
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                vm.refreshNow()
                delay(1000)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = {}, label = { Text(t.label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Accueil -> HomeScreen(home)
                Tab.Reglages -> SettingsScreen(vm)
                Tab.Journal -> JournalScreen(vm)
                Tab.Debug -> DebugScreen(vm)
            }
        }
    }
}
