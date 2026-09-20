package rehab.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HoldButton(holdMillis: Long, label: String, color: Color, onCompleted: () -> Unit, modifier: Modifier = Modifier) {
    var pressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pressing) {
        if (!pressing) { progress = 0f; return@LaunchedEffect }
        val start = withFrameMillis { it }
        while (pressing) {
            val now = withFrameMillis { it }
            progress = ((now - start).toFloat() / holdMillis).coerceIn(0f, 1f)
            if (progress >= 1f) {
                pressing = false
                onCompleted()
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.25f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressing = true
                    tryAwaitRelease()
                    pressing = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(96.dp),
            color = color.copy(alpha = 0.6f),
            trackColor = Color.Transparent,
        )
        Text(label, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
    }
}
