package rehab.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import rehab.app.service.LastDetection
import rehab.app.service.RehabAccessibilityService
import rehab.app.ui.components.AccentButton
import rehab.app.ui.components.HeaderCaption
import rehab.app.ui.components.KeyValue
import rehab.app.ui.components.KeyValueGroup
import rehab.app.ui.components.Note
import rehab.app.ui.components.RehabHeader
import rehab.app.ui.components.RehabText
import rehab.app.ui.components.SecondaryButton
import rehab.app.ui.components.SectionLabel
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors
import kotlin.math.ceil

/**
 * Écran Debug (`docs/design/mockups/Debug.dc.html`), sans dépendance au ViewModel : reçoit toutes
 * ses valeurs en paramètres et notifie ses actions par callback. Composable pur, testable/rendable
 * sans Room ni coroutine (voir RenderDebug) — c'est [DebugScreen], seul, qui touche `vm`/`graph`
 * et le partage de fichiers (FileProvider).
 */
@Composable
fun DebugContent(
    delayText: String,
    onDelayChange: (String) -> Unit,
    pendingAt: Long?,
    nowMillis: Long,
    last: LastDetection?,
    decisionSummary: String,
    captures: List<CaptureFile>,
    serviceAvailable: Boolean,
    onCapture: () -> Unit,
    onExport: () -> Unit,
    onOverlay: () -> Unit,
    onShareOne: (CaptureFile) -> Unit,
) {
    val delayValid = delayText.toIntOrNull()?.let { it in 1..60 } == true
    val captureLabel = when {
        pendingAt == null -> "Capturer le prochain écran cible"
        pendingAt > nowMillis -> "En attente · ${ceil((pendingAt - nowMillis) / 1000.0).toLong()} s"
        else -> "En attente d'un écran cible…"
    }
    val captureEnabled = pendingAt == null && delayValid

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RehabHeader { HeaderCaption("outils") }

        SectionLabel("Capture de la structure")
        Column(
            Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(16.dp))
                .background(RehabColors.Panel).padding(vertical = 12.dp, horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Délai avant capture", style = RehabText.body14)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(52.dp, 36.dp).background(RehabColors.Bg, RoundedCornerShape(10.dp))
                            .border(1.dp, RehabColors.Line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicTextField(
                            value = delayText,
                            onValueChange = onDelayChange,
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = ChivoMono, fontSize = 15.sp, color = RehabColors.Text, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(RehabColors.Text),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text("S", style = RehabText.caps12)
                }
            }
        }
        AccentButton(
            captureLabel, onCapture,
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 10.dp),
            enabled = captureEnabled,
        )
        Note("Ouvre Instagram ou X pendant le délai : le prochain Snapshot est sérialisé en JSON dans files/captures/.")

        SectionLabel("Dernière détection · en direct")
        KeyValueGroup(detectionItems(last, decisionSummary, nowMillis))

        SectionLabel("Captures · ${captures.size}")
        if (captures.isEmpty()) {
            Note("Aucune capture.")
        } else {
            KeyValueGroup(captures.map { c -> KeyValue(c.name, DebugText.size(c.sizeBytes), onClick = { onShareOne(c) }) })
        }
        Row(Modifier.padding(horizontal = 24.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Exporter", onExport, Modifier.weight(1f), enabled = captures.isNotEmpty())
            SecondaryButton("Overlay de test", onOverlay, Modifier.weight(1f), enabled = serviceAvailable)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun detectionItems(last: LastDetection?, decisionSummary: String, nowMillis: Long): List<KeyValue> = if (last == null) {
    listOf(KeyValue("Aucune détection", "ouvre Instagram ou X"))
} else {
    buildList {
        add(KeyValue("Package", last.packageName))
        add(KeyValue("Version", last.appVersion))
        add(KeyValue("targetId", last.target ?: "—", valueColor = RehabColors.Accent))
        add(KeyValue("unknownScreen", last.unknownScreen.toString()))
        add(KeyValue("Décision", decisionSummary))
        add(KeyValue("Il y a", DebugText.ago(nowMillis, last.atMillis)))
        if (last.degraded) add(KeyValue("Mode dégradé", last.degradedReason.orEmpty(), valueColor = RehabColors.Warn))
    }
}

/**
 * Wrapper avec état : lit `graph` uniquement via [vm] (`detectionLast`, `capturePendingAt`,
 * `captureLastFile`, `captures()`), garde le délai saisi (`rememberSaveable`, survit à une
 * rotation) et construit les intentions de partage (FileProvider). `DebugContent` reste un
 * Composable pur, sans accès à `graph`/Room/`Context` — voir sa doc.
 */
@Composable
fun DebugScreen(vm: RehabViewModel, home: HomeUiState) {
    val context = LocalContext.current
    val last by vm.detectionLast.collectAsState()
    val pendingAt by vm.capturePendingAt.collectAsState()
    val lastFile by vm.captureLastFile.collectAsState()
    var delayText by rememberSaveable { mutableStateOf("5") }
    var captures by remember { mutableStateOf<List<CaptureFile>>(emptyList()) }
    val service = RehabAccessibilityService.instance

    // Recharge la liste à chaque nouvelle capture (dont la toute première composition, `lastFile`
    // valant alors sa valeur courante, éventuellement non nulle si des captures existent déjà).
    LaunchedEffect(lastFile) { captures = vm.captures() }

    fun share(files: List<CaptureFile>) {
        val uris = files.map { FileProvider.getUriForFile(context, "rehab.app.files", it.file) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/json"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Partager les captures"))
    }

    DebugContent(
        delayText = delayText,
        onDelayChange = { delayText = it },
        pendingAt = pendingAt,
        nowMillis = home.nowMillis,
        last = last,
        decisionSummary = home.decisionSummary,
        captures = captures,
        serviceAvailable = service != null,
        onCapture = { delayText.toIntOrNull()?.let { vm.requestCapture(it * 1000L) } },
        onExport = { share(captures) },
        onOverlay = { service?.showTestOverlay() },
        onShareOne = { share(listOf(it)) },
    )
}
