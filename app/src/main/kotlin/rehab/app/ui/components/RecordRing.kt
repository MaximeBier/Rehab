package rehab.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rehab.app.ui.theme.RehabColors

// Anneau plein si fraction = 1 ; arc depuis 12 h sinon ; rien dessiné pour un arc de longueur 0 (pas de point).
@Composable
fun RecordRing(
    size: Dp,
    stroke: Dp,
    ringColor: Color,
    fraction: Float = 1f,
    trackColor: Color = Color.Transparent,
    overlayArc: Float = 0f,
    overlayArcColor: Color = RehabColors.Danger,
    ringAlpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val inset = w / 2 + 4.dp.toPx()
            val topLeft = Offset(inset, inset)
            val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            val style = Stroke(width = w)
            if (trackColor.alpha > 0f) drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = style)
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) drawArc(ringColor, -90f, 360f * f, false, topLeft, arcSize, alpha = ringAlpha, style = style)
            val o = overlayArc.coerceIn(0f, 1f)
            if (o > 0f) drawArc(overlayArcColor, -90f, 360f * o, false, topLeft, arcSize, alpha = 0.9f, style = style)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
