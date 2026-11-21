package rehab.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rehab.app.ui.theme.Chivo
import rehab.app.ui.theme.RehabColors

/**
 * Emplacement fixe de 44 dp sous la légende du bouton d'appui (DESIGN §5 « Accès aux messages », planche
 * `Overlay-Messages`). Réservé même sans lien ([onClick] nul : app sans ouverture fiable) pour que rien ne bouge.
 */
@Composable
fun MessagesSlot(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
        if (onClick != null) MessagesLink(onClick)
    }
}

/**
 * Lien texte « Ouvrir mes messages » : Chivo 13 sp `muted`, souligné 1 dp à 3 dp sous la ligne de base
 * (`TextDecoration.Underline` ne permet pas ce décalage). Cible : 44 dp de haut, texte + 12 dp de chaque côté.
 * Pressé : texte `text` sur fond `panel`. Jamais atténué ni désactivé ; ne rougit pas pendant l'appui relapse.
 */
@Composable
private fun MessagesLink(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val color = if (pressed) RehabColors.Text else RehabColors.Muted
    var baseline by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) RehabColors.Panel else Color.Transparent)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Ouvrir mes messages",
            style = TextStyle(fontFamily = Chivo, fontSize = 13.sp, fontWeight = FontWeight.W400, color = color, textAlign = TextAlign.Center),
            onTextLayout = { baseline = it.lastBaseline },
            modifier = Modifier.drawBehind {
                val y = baseline + 3.dp.toPx()
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            },
        )
    }
}
