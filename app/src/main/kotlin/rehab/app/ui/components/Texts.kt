package rehab.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import rehab.app.ui.theme.Chivo
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors

/** Styles de texte partagés, DESIGN.md §2 (typographie). */
object RehabText {
    val brand = TextStyle(fontFamily = Chivo, fontSize = 13.sp, fontWeight = FontWeight.W500, letterSpacing = 0.16.em, color = RehabColors.Text)
    val section = TextStyle(fontFamily = Chivo, fontSize = 11.sp, letterSpacing = 0.14.em, color = RehabColors.Muted)
    val caps12 = TextStyle(fontFamily = Chivo, fontSize = 12.sp, letterSpacing = 0.08.em, color = RehabColors.Muted)
    val ringLabel = TextStyle(fontFamily = Chivo, fontSize = 11.sp, fontWeight = FontWeight.W700, letterSpacing = 0.2.em)
    val ringUnit = TextStyle(fontFamily = Chivo, fontSize = 11.sp, letterSpacing = 0.16.em, color = RehabColors.Muted)
    val body13 = TextStyle(fontFamily = Chivo, fontSize = 13.sp, color = RehabColors.Text)
    val body14 = TextStyle(fontFamily = Chivo, fontSize = 14.sp, color = RehabColors.Text)
    val small11 = TextStyle(fontFamily = Chivo, fontSize = 11.sp, color = RehabColors.Muted)
    val note = TextStyle(fontFamily = Chivo, fontSize = 12.sp, lineHeight = 18.sp, color = RehabColors.Muted)
    val mono13 = TextStyle(fontFamily = ChivoMono, fontSize = 13.sp, color = RehabColors.Text)
    val mono14 = TextStyle(fontFamily = ChivoMono, fontSize = 14.sp, color = RehabColors.Text)
}

private val numberRun = Regex("""\d+(?:[/:.,]\d+)*""")

/** Passe en Chivo Mono chaque suite de chiffres (« 23 », « 2/2 », « 07:30 ») : DESIGN §2, valeurs en mono dans le texte courant. */
fun withMonoNumbers(text: String, numberColor: Color? = null): AnnotatedString = buildAnnotatedString {
    append(text)
    numberRun.findAll(text).forEach {
        addStyle(SpanStyle(fontFamily = ChivoMono, color = numberColor ?: Color.Unspecified), it.range.first, it.range.last + 1)
    }
}

@Composable
fun SectionLabel(text: String) =
    Text(text.uppercase(), style = RehabText.section, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp))

@Composable
fun Note(text: String) =
    Text(text, style = RehabText.note, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 10.dp))
