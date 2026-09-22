package rehab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors

data class KeyValue(
    val key: String,
    val value: String,
    val valueColor: Color = RehabColors.Text,
    val keyMono: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun KeyValueGroup(items: List<KeyValue>) {
    Column(Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(16.dp))) {
        items.forEachIndexed { index, item ->
            KeyValueRow(item)
            if (index != items.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(RehabColors.Bg))
            }
        }
    }
}

@Composable
private fun KeyValueRow(item: KeyValue) {
    // defaultMinSize garantit la cible tactile >= 44 dp (DESIGN §2) sur les lignes cliquables :
    // le padding vertical de 11 dp seul ne les amène qu'à ~39 dp.
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(RehabColors.Panel)
        .let { if (item.onClick != null) it.clickable(onClick = item.onClick).defaultMinSize(minHeight = 44.dp) else it }
        .padding(vertical = 11.dp, horizontal = 16.dp)
    // La clé (gauche) ne porte aucun weight : elle garde sa largeur de contenu. La valeur (droite)
    // porte le weight(1f) et s'aligne à droite : une valeur longue (ex. « com.instagram.android »)
    // passe à la ligne dans son propre espace plutôt que de compresser la clé (même règle que
    // SettingsGroup pour la ligne « Dimanche »).
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            item.key,
            style = RehabText.body13.copy(color = RehabColors.Muted, fontFamily = if (item.keyMono) ChivoMono else RehabText.body13.fontFamily),
        )
        Text(
            item.value,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            style = RehabText.mono13.copy(color = item.valueColor),
        )
    }
}
