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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rehab.app.di.AppGraph
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.policy.GuardResult
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(SettingsForm.from(graph.settingsRepo.get())) }
    var message by remember { mutableStateOf<String?>(null) }

    val now = graph.clock.now()
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val lockedNightRow: DayOfWeek? = graph.schedule.activeNight(now)?.row?.dayOfWeek
    val lockedNightUntil = graph.schedule.activeNight(now)?.end?.atZone(graph.clock.zone())?.format(hm)
    val quotaBlock = (graph.policy.evaluate(now) as? Decision.Block)?.takeIf { it.reason == BlockReason.Quota }

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
        if (quotaBlock != null) Text("Quota en cours : plafonds non relevables avant ${quotaBlock.unlockAt.atZone(graph.clock.zone()).format(hm)}.", style = MaterialTheme.typography.bodySmall)
        form.quotas.forEachIndexed { i, (dur, cap) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                OutlinedTextField(cap, { form = form.copy(quotas = form.quotas.toMutableList().also { l -> l[i] = it to dur }) }, Modifier.width(96.dp), label = { Text("Max") }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(dur, { form = form.copy(quotas = form.quotas.toMutableList().also { l -> l[i] = cap to it }) }, Modifier.width(96.dp), label = { Text("Sur") }, singleLine = true)
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
                    val result = withContext(Dispatchers.IO) { graph.settingsGuard.validate(proposed, graph.clock.now()) }
                    when (result) {
                        GuardResult.Accepted -> { graph.settingsRepo.set(proposed); message = "Enregistré." }
                        is GuardResult.Rejected -> message = "${result.reason} : modifiable à partir de ${result.unlockAt.atZone(graph.clock.zone()).format(hm)}."
                    }
                }
            }
        }) { Text("Enregistrer") }
        message?.let { Text(it, Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(32.dp))
    }
}
