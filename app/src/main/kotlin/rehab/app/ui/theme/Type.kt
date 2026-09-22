package rehab.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import rehab.app.R

// `variationSettings` sur `Font` est expérimental dans cette version de Compose (ExperimentalTextApi).
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Chivo = FontFamily(
    variable(R.font.chivo, 300), variable(R.font.chivo, 400), variable(R.font.chivo, 500), variable(R.font.chivo, 700),
)

val ChivoMono = FontFamily(
    variable(R.font.chivo_mono, 300), variable(R.font.chivo_mono, 400), variable(R.font.chivo_mono, 500),
)
