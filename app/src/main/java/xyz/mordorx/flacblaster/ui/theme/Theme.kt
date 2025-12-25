package xyz.mordorx.flacblaster.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import cafe.adriel.bonsai.core.BonsaiStyle

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    /* Other default colors to override

    */
)

var ActiveColorScheme: ColorScheme = DarkColorScheme

@Composable
fun FLACblasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    ActiveColorScheme = colorScheme


    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun bonsaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme()
): BonsaiStyle<String> {
    val scheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val tint = ColorFilter.tint(scheme.onSurface)
    return BonsaiStyle(
        nodeNameTextStyle = TextStyle(scheme.onSurface),
        toggleIconColorFilter = tint,
        nodeCollapsedIconColorFilter = tint,
        nodeExpandedIconColorFilter = tint,
        nodeSelectedBackgroundColor = scheme.background,
        )
}