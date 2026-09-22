package rehab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(RehabColors.Panel)
        .let { if (item.onClick != null) it.clickable(onClick = item.onClick) else it }
        .padding(vertical = 11.dp, horizontal = 16.dp)
    Row(rowModifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            item.key,
            style = RehabText.body13.copy(color = RehabColors.Muted, fontFamily = if (item.keyMono) ChivoMono else RehabText.body13.fontFamily),
        )
        Text(item.value, style = RehabText.mono13.copy(color = item.valueColor))
    }
}
