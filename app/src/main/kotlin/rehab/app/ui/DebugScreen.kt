package rehab.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import rehab.app.service.RehabAccessibilityService
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Aucune lecture Room ici : les seules données consultées sont `graph.detectionState.last` et
 * `graph.capture.*`, exposées par [RehabViewModel] via des `StateFlow` (pas de base de données
 * derrière) ou une méthode dédiée hors thread principal (`captureCount`). `graph` lui-même reste
 * privé au ViewModel (voir le commentaire de tête de `RehabViewModel`).
 */
@Composable
fun DebugScreen(vm: RehabViewModel) {
    val context = LocalContext.current
    val last by vm.detectionLast.collectAsState()
    val pending by vm.capturePendingAt.collectAsState()
    val lastFile by vm.captureLastFile.collectAsState()
    val zone = remember { vm.zone() }
    val hms = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    var captureCount by remember { mutableStateOf(0) }

    LaunchedEffect(lastFile) { captureCount = vm.captureCount() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Dernière détection", style = MaterialTheme.typography.titleMedium)
        if (last == null) {
            Text("Aucune. Ouvre Instagram ou X.")
        } else {
            last?.let { d ->
                Text("${Instant.ofEpochMilli(d.atMillis).atZone(zone).format(hms)} · ${d.packageName} ${d.appVersion}")
                Text("Écran : ${d.screenId ?: "inconnu"} · Cible : ${d.target ?: "aucune"}")
                Text("Inconnu : ${d.unknownScreen} · Dégradé : ${d.degraded}")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Capturer la structure de l'écran", style = MaterialTheme.typography.titleMedium)
        Text(
            "Lance le compte à rebours, bascule sur l'écran voulu dans Instagram ou X. Le premier snapshot après l'échéance est enregistré en JSON.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button({ vm.requestCapture(delayMillis = 5000) }, enabled = pending == null) {
            Text(if (pending == null) "Capturer dans 5 s" else "En attente d'un écran cible…")
        }
        lastFile?.let { f ->
            Text("Dernière capture : ${f.name}")
            OutlinedButton({
                val uri = FileProvider.getUriForFile(context, "rehab.app.files", f)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Partager la capture"))
            }) { Text("Partager") }
        }
        Text(HomeText.plural(captureCount, "capture"), style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(24.dp))
        Text("Overlay", style = MaterialTheme.typography.titleMedium)
        val service = RehabAccessibilityService.instance
        Button({ service?.showTestOverlay() }, enabled = service != null) { Text("Afficher un overlay de test (5 s)") }
        if (service == null) Text("Service inactif.", style = MaterialTheme.typography.bodySmall)
    }
}
