package rehab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import rehab.app.ui.theme.Chivo
import rehab.app.ui.theme.RehabColors

@Composable
fun RehabNavBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
            .clip(RoundedCornerShape(22.dp)).background(RehabColors.Panel).padding(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (active) RehabColors.Accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(), maxLines = 1, softWrap = false,
                    style = TextStyle(
                        fontFamily = Chivo, fontSize = 11.sp, letterSpacing = 0.1.em,
                        fontWeight = if (active) FontWeight.W700 else FontWeight.W400,
                        color = if (active) RehabColors.Bg else RehabColors.Muted,
                    ),
                )
            }
        }
    }
}
