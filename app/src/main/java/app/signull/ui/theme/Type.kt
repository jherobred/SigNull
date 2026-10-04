package app.signull.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.signull.R

/** Google Sans Display: headlines and big numbers. */
val GoogleSansDisplay = FontFamily(
    Font(R.font.google_sans_display_regular, FontWeight.Normal),
    Font(R.font.google_sans_display_medium, FontWeight.Medium),
    Font(R.font.google_sans_display_bold, FontWeight.Bold),
)

/** Google Sans Text: body copy and labels at small sizes. */
val GoogleSansText = FontFamily(
    Font(R.font.google_sans_text_regular, FontWeight.Normal),
    Font(R.font.google_sans_text_medium, FontWeight.Medium),
    Font(R.font.google_sans_text_bold, FontWeight.Bold),
)

private val base = Typography()

val SigNullTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = GoogleSansDisplay),
    displayMedium = base.displayMedium.copy(fontFamily = GoogleSansDisplay),
    displaySmall = base.displaySmall.copy(fontFamily = GoogleSansDisplay),
    headlineLarge = base.headlineLarge.copy(fontFamily = GoogleSansDisplay),
    headlineMedium = base.headlineMedium.copy(fontFamily = GoogleSansDisplay),
    headlineSmall = base.headlineSmall.copy(fontFamily = GoogleSansDisplay),
    titleLarge = base.titleLarge.copy(fontFamily = GoogleSansDisplay),
    titleMedium = base.titleMedium.copy(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.copy(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = GoogleSansText),
    bodyMedium = base.bodyMedium.copy(fontFamily = GoogleSansText),
    bodySmall = base.bodySmall.copy(fontFamily = GoogleSansText),
    labelLarge = base.labelLarge.copy(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = GoogleSansText, fontWeight = FontWeight.Medium),
)

/** Tabular figures keep live numbers from jittering as digits change. */
val NumberStyle = TextStyle(
    fontFamily = GoogleSansDisplay,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
)

val HeroNumberStyle = NumberStyle.copy(fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-1.5).sp)
