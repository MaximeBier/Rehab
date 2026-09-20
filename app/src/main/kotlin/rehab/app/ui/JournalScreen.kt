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

@Composable
fun JournalScreen(vm: RehabViewModel) {
    var lines by remember { mutableStateOf<List<JournalLine>>(emptyList()) }
    LaunchedEffect(Unit) { lines = vm.loadJournal() }
    LazyColumn(Modifier.padding(16.dp)) {
        items(lines) { Text(it.text, Modifier.padding(vertical = 4.dp)) }
    }
}
