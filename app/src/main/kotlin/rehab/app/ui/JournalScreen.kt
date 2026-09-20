package rehab.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rehab.app.di.AppGraph

@Composable
fun JournalScreen(graph: AppGraph) {
    var lines by remember { mutableStateOf<List<JournalLine>>(emptyList()) }
    LaunchedEffect(Unit) {
        lines = withContext(Dispatchers.IO) {
            val zone = graph.clock.zone()
            val events = graph.eventLog.all().map { JournalLine(it.at.toEpochMilli(), JournalText.line(it, zone)) }
            val usage = graph.usageLog.latest(200).map { JournalLine(it.start.toEpochMilli(), JournalText.line(it, zone)) }
            (events + usage).sortedByDescending { it.atMillis }
        }
    }
    LazyColumn(Modifier.padding(16.dp)) {
        items(lines) { Text(it.text, Modifier.padding(vertical = 4.dp)) }
    }
}
