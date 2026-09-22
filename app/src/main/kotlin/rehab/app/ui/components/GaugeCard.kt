package rehab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors

@Composable
fun GaugeCard(label: String, value: String, fraction: Float, exceeded: Boolean) {
    val color = if (exceeded) RehabColors.Danger else RehabColors.Accent
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RehabColors.Panel).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label.uppercase(), style = RehabText.caps12)
            Text(value.uppercase(), style = RehabText.caps12.copy(fontFamily = ChivoMono, color = if (exceeded) RehabColors.Danger else RehabColors.Text))
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(RehabColors.Bg)) {
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}
