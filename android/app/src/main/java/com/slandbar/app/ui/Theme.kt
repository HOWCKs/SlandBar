package com.slandbar.app.ui

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Tema da SlandBar: Material 3 com cores dinâmicas (Material You),
 * tema escuro/claro e cor de destaque personalizada (Premium).
 */
@Composable
fun SlandTheme(
    dark: Boolean,
    useDynamic: Boolean,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        accent != null -> {
            val base = if (dark) darkColorScheme() else lightColorScheme()
            base.copy(
                primary = accent,
                secondary = accent.copy(alpha = 0.85f),
                tertiary = accent.copy(alpha = 0.7f)
            )
        }
        else -> if (dark) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

/** Converte uma cor em hex (#RRGGBB) para Color, com fallback seguro. */
fun parseAccentColor(hex: String?): Color? = hex?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}
