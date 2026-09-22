package rehab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import rehab.app.ui.theme.Chivo
import rehab.app.ui.theme.RehabColors

@Composable
fun AlertBanner(text: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RehabColors.WarnBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.sp, color = RehabColors.Warn, modifier = Modifier.weight(1f))
        if (action != null) {
            Box(
                Modifier.defaultMinSize(minHeight = 44.dp).clickable(onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    action.uppercase(),
                    fontFamily = Chivo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.06.em,
                    color = RehabColors.Warn,
                )
            }
        }
    }
}
