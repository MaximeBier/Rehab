package rehab.app.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
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
    var tab by rememberSaveable { mutableStateOf(Tab.Accueil) }
    val home by vm.home.collectAsState()

    // Rafraîchissement lié au cycle de vie : ne tourne (une lecture Room par seconde) que tant
    // que l'écran est visible (STARTED), pas en continu écran éteint ou app en arrière-plan.
    val lifecycleOwner = LocalLifecycleOwner.current
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
                Tab.Debug -> Text("Debug (à venir)")
            }
        }
    }
}
