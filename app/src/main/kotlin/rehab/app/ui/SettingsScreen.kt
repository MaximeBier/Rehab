package rehab.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import rehab.domain.model.Settings
import rehab.domain.policy.GuardResult
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Toutes les données qui exigent une lecture Room (réglages, verrous nuit/quota) sont chargées
 * via [RehabViewModel.loadSettingsScreen]/[RehabViewModel.loadLocks], jamais dans le corps du
 * Composable : celui-ci s'exécute sur le thread principal à chaque recomposition (chaque frappe),
 * et la base de production n'autorise pas les requêtes sur ce thread.
 *
 * Les verrous (nuit active, quota en cours) sont recalculés chaque seconde tant que l'écran est
 * visible, sur le même principe que le rafraîchissement de l'Accueil dans `MainActivity`
 * (`repeatOnLifecycle(STARTED)` + `delay(1000)`) : sans ça, un utilisateur qui reste sur l'écran
 * pendant qu'une nuit ou un quota démarre ne verrait aucun cadenas et ne comprendrait pas le refus
 * à l'enregistrement. Seuls les verrous sont rafraîchis par cette boucle, jamais `form` : la saisie
 * en cours de l'utilisateur n'est chargée qu'une fois, à l'ouverture de l'écran.
 */
@Composable
fun SettingsScreen(vm: RehabViewModel) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(SettingsForm.from(Settings.DEFAULT)) }
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    var lockedNightRow by remember { mutableStateOf<DayOfWeek?>(null) }
    var lockedNightEnd by remember { mutableStateOf<Instant?>(null) }
    var quotaUnlockAt by remember { mutableStateOf<Instant?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val state = vm.loadSettingsScreen()
        form = state.form
        zone = state.zone
        lockedNightRow = state.locks.lockedNightRow
        lockedNightEnd = state.locks.lockedNightEnd
        quotaUnlockAt = state.locks.quotaUnlockAt
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val locks = vm.loadLocks()
                lockedNightRow = locks.lockedNightRow
                lockedNightEnd = locks.lockedNightEnd
                quotaUnlockAt = locks.quotaUnlockAt
                delay(1000)
            }
        }
    }

    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val lockedNightUntil = lockedNightEnd?.atZone(zone)?.format(hm)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Nuit (coucher → lever du lendemain)", style = MaterialTheme.typography.titleMedium)
        DayOfWeek.entries.forEach { day ->
            val (bed, wake) = form.nights.getValue(day)
            val locked = day == lockedNightRow
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(SettingsForm.dayName(day) + if (locked) " 🔒" else "", Modifier.width(96.dp).padding(top = 20.dp))
                OutlinedTextField(bed, { form = form.copy(nights = form.nights + (day to (it to wake))) }, Modifier.width(96.dp), label = { Text("Coucher") }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(wake, { form = form.copy(nights = form.nights + (day to (bed to it))) }, Modifier.width(96.dp), label = { Text("Lever") }, singleLine = true)
            }
            if (locked) Text("Plage en cours : seulement allongeable. Modifiable à partir de $lockedNightUntil.", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Text("Quota glissant (minutes)", style = MaterialTheme.typography.titleMedium)
        quotaUnlockAt?.let { Text("Quota en cours : plafonds non relevables avant ${it.atZone(zone).format(hm)}.", style = MaterialTheme.typography.bodySmall) }
        form.quotas.forEachIndexed { i, (dur, cap) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(cap, { form = form.copy(quotas = SettingsForm.withQuotaCap(form.quotas, i, it)) }, Modifier.width(96.dp), label = { Text("Max") }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(dur, { form = form.copy(quotas = SettingsForm.withQuotaDuration(form.quotas, i, it)) }, Modifier.width(96.dp), label = { Text("Sur") }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                OutlinedButton({ form = form.copy(quotas = form.quotas.filterIndexed { j, _ -> j != i }) }, Modifier.padding(top = 8.dp)) { Text("−") }
            }
        }
        OutlinedButton({ form = form.copy(quotas = form.quotas + ("60" to "10")) }) { Text("Ajouter une fenêtre") }

        Spacer(Modifier.height(16.dp))
        Text("Joker et relapse", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(form.jokerMinutes, { form = form.copy(jokerMinutes = it) }, label = { Text("Durée du joker (min)") }, singleLine = true)
        OutlinedTextField(form.relapseMinutes, { form = form.copy(relapseMinutes = it) }, label = { Text("Durée du relapse (min)") }, singleLine = true)
        OutlinedTextField(form.jokersPerDay, { form = form.copy(jokersPerDay = it) }, label = { Text("Jokers par jour") }, singleLine = true)
        OutlinedTextField(form.holdSeconds, { form = form.copy(holdSeconds = it) }, label = { Text("Durée d'appui (s)") }, singleLine = true)

        Spacer(Modifier.height(16.dp))
        Button({
            val parsed = form.toSettings()
            parsed.onFailure { message = it.message }.onSuccess { proposed ->
                scope.launch {
                    when (val result = vm.saveSettings(proposed)) {
                        GuardResult.Accepted -> message = "Enregistré."
                        is GuardResult.Rejected -> message = "${result.reason} : modifiable à partir de ${result.unlockAt.atZone(zone).format(hm)}."
                    }
                }
            }
        }) { Text("Enregistrer") }
        message?.let { Text(it, Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(32.dp))
    }
}
