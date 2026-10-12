package io.github.halilozel1903.deezer.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** A warm coral theme for Pocket Charts. */
@Composable
fun SampleTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFFFFB4A8),
            onPrimary = Color(0xFF5F1410),
            primaryContainer = Color(0xFF7E2B24),
            onPrimaryContainer = Color(0xFFFFDAD5),
            secondary = Color(0xFFE7BDB7),
            secondaryContainer = Color(0xFF5D3F3B),
            onSecondaryContainer = Color(0xFFFFDAD5),
            tertiary = Color(0xFFDFC38C),
            background = Color(0xFF1A1110),
            onBackground = Color(0xFFF1DFDC),
            surface = Color(0xFF1A1110),
            onSurface = Color(0xFFF1DFDC),
            surfaceVariant = Color(0xFF534341),
            onSurfaceVariant = Color(0xFFD8C2BE),
            surfaceContainerLow = Color(0xFF231918),
            surfaceContainer = Color(0xFF271D1C),
            surfaceContainerHigh = Color(0xFF322827),
            surfaceContainerHighest = Color(0xFF3D3231),
            outline = Color(0xFFA08C89),
            outlineVariant = Color(0xFF534341),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFB3261E),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDAD5),
            onPrimaryContainer = Color(0xFF410001),
            secondary = Color(0xFF775652),
            secondaryContainer = Color(0xFFFFDAD5),
            onSecondaryContainer = Color(0xFF2C1512),
            tertiary = Color(0xFF715B2E),
            background = Color(0xFFFFF8F6),
            onBackground = Color(0xFF231918),
            surface = Color(0xFFFFF8F6),
            onSurface = Color(0xFF231918),
            surfaceVariant = Color(0xFFF5DDDA),
            onSurfaceVariant = Color(0xFF534341),
            surfaceContainerLow = Color(0xFFFFF0EE),
            surfaceContainer = Color(0xFFFCEAE7),
            surfaceContainerHigh = Color(0xFFF6E4E1),
            surfaceContainerHighest = Color(0xFFF1DFDC),
            outline = Color(0xFF857370),
            outlineVariant = Color(0xFFD8C2BE),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
