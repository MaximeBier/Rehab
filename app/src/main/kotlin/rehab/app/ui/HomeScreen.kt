package rehab.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(state: HomeUiState) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        state.alerts.forEach { alert ->
            Card(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) { Text(alert, Modifier.padding(12.dp)) }
        }
        Text("${state.streak} jours", style = MaterialTheme.typography.displayMedium)
        Text("sans relapse · record ${state.best}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Text(state.status, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Quota", style = MaterialTheme.typography.titleMedium)
        state.quotaLines.forEach { Text(it) }
        Spacer(Modifier.height(16.dp))
        Text("Jokers restants aujourd'hui : ${state.jokersLeft}")
    }
}
