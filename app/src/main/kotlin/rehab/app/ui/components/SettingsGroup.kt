package rehab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import rehab.app.ui.theme.RehabColors

data class RowSpec(
    val label: String,
    val value: String,
    val subtitle: String? = null,
    val lockedReason: String? = null,
    val valueColor: Color? = null,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun SettingsGroup(rows: List<RowSpec>, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(16.dp))) {
        rows.forEachIndexed { index, row ->
            SettingsRow(row)
            if (index != rows.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(RehabColors.Bg))
            }
        }
    }
}

@Composable
private fun SettingsRow(row: RowSpec) {
    val locked = row.lockedReason != null
    val onClick = row.onClick.takeIf { !locked }
    // defaultMinSize garantit la cible tactile >= 44 dp (DESIGN §2) sur les lignes cliquables ;
    // le padding vertical de 14 dp seul suffit déjà dans la plupart des cas mais pas toujours
    // (libellé sans sous-titre en petite taille de police).
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(RehabColors.Panel)
        .let { if (onClick != null) it.clickable(onClick = onClick).defaultMinSize(minHeight = 44.dp) else it }
        .padding(vertical = 14.dp, horizontal = 16.dp)
    Row(rowModifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
            if (locked) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = RehabColors.Muted)
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(row.label, style = RehabText.body14.copy(color = if (locked) RehabColors.Muted else RehabColors.Text))
                row.subtitle?.let { Text(it, style = RehabText.small11) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text(row.value, style = RehabText.mono14.copy(color = if (locked) RehabColors.Muted else row.valueColor ?: RehabColors.Text))
                if (locked) Text(row.lockedReason.orEmpty(), style = RehabText.small11)
            }
            if (!locked && row.onClick != null) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = RehabColors.Muted)
            }
        }
    }
}
