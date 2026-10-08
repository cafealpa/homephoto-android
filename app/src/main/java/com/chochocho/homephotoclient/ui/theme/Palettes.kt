package com.chochocho.homephotoclient.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.chochocho.homephotoclient.data.AppPalette

private data class Palette(
    val background: Color, val surface: Color, val card: Color,
    val text: Color, val muted: Color, val accent: Color,
    val onAccent: Color, val tint: Color, val outline: Color,
)

private fun palette(bg: Long, surface: Long, card: Long, text: Long, muted: Long,
                    accent: Long, onAccent: Long, tint: Long, outline: Long) =
    Palette(Color(bg), Color(surface), Color(card), Color(text), Color(muted),
        Color(accent), Color(onAccent), Color(tint), Color(outline))

// 승인된 6가지 시안의 노멀/다크 색상 쌍.
private val palettes = mapOf(
    AppPalette.PEACH to Pair(
        palette(0xFFFFF9F5, 0xFFFFFFFF, 0xFFF9EFE7, 0xFF2C211C, 0xFF766258, 0xFFA44A27, 0xFFFFFFFF, 0xFFFFE3D3, 0xFFD9C7BB),
        palette(0xFF191411, 0xFF231C18, 0xFF302620, 0xFFF7EAE2, 0xFFC9B4A7, 0xFFFFB28C, 0xFF3B1D10, 0xFF563320, 0xFF665146)),
    AppPalette.SAGE to Pair(
        palette(0xFFF7F9F5, 0xFFFFFFFF, 0xFFEDF2E9, 0xFF202A21, 0xFF5F6F60, 0xFF426548, 0xFFFFFFFF, 0xFFDCEBD8, 0xFFBDCABD),
        palette(0xFF141A15, 0xFF1D251E, 0xFF29342B, 0xFFE9F0E7, 0xFFB4C5B5, 0xFFA4D2A3, 0xFF142A18, 0xFF304C34, 0xFF4E6452)),
    AppPalette.BLUE to Pair(
        palette(0xFFF7F9FD, 0xFFFFFFFF, 0xFFEDF2FA, 0xFF202B3C, 0xFF5C6C82, 0xFF315F9B, 0xFFFFFFFF, 0xFFDCE9FB, 0xFFBFCBDD),
        palette(0xFF111822, 0xFF1A2532, 0xFF253344, 0xFFE7EEF9, 0xFFB2C3DB, 0xFFA8C9FF, 0xFF102E55, 0xFF29466C, 0xFF4A607D)),
    AppPalette.LAVENDER to Pair(
        palette(0xFFFAF8FD, 0xFFFFFFFF, 0xFFF0ECF6, 0xFF2C2537, 0xFF70647D, 0xFF73548E, 0xFFFFFFFF, 0xFFECDEF8, 0xFFCEC2D9),
        palette(0xFF1A161F, 0xFF251F2D, 0xFF32293D, 0xFFF1EAF7, 0xFFC9BBD6, 0xFFD6B9F2, 0xFF3A214E, 0xFF513862, 0xFF685574)),
    AppPalette.SAND to Pair(
        palette(0xFFFAF8F3, 0xFFFFFFFF, 0xFFF1EBE0, 0xFF302A23, 0xFF746959, 0xFF766044, 0xFFFFFFFF, 0xFFEBDFC8, 0xFFCFC3AF),
        palette(0xFF1B1813, 0xFF262119, 0xFF332C22, 0xFFF1EBDF, 0xFFC9BDAC, 0xFFDCC19A, 0xFF352818, 0xFF51422D, 0xFF6A5B45)),
    AppPalette.MONO to Pair(
        palette(0xFFF7F7F7, 0xFFFFFFFF, 0xFFECEDEE, 0xFF202124, 0xFF64686D, 0xFF41464D, 0xFFFFFFFF, 0xFFE1E3E6, 0xFFC4C7CC),
        palette(0xFF121315, 0xFF1D1F22, 0xFF2A2D31, 0xFFEEEFF1, 0xFFBCC0C6, 0xFFD3D7DF, 0xFF25292F, 0xFF3D424B, 0xFF5C626D)),
)

private fun Palette.scheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = tint, onPrimaryContainer = accent, inversePrimary = onAccent,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = tint, onSecondaryContainer = accent,
        tertiary = accent, onTertiary = onAccent,
        tertiaryContainer = tint, onTertiaryContainer = accent,
        background = background, onBackground = text,
        surface = background, onSurface = text, surfaceVariant = card, onSurfaceVariant = muted,
        surfaceTint = accent, inverseSurface = text, inverseOnSurface = background,
        outline = outline, outlineVariant = lerp(background, outline, 0.5f),
        surfaceContainerLowest = surface, surfaceContainerLow = surface,
        surfaceContainer = card,
        surfaceContainerHigh = if (dark) lerp(card, text, 0.025f) else lerp(card, surface, 0.15f),
        surfaceContainerHighest = if (dark) lerp(card, text, 0.045f) else lerp(card, surface, 0.3f),
        surfaceBright = if (dark) lerp(card, text, 0.045f) else surface,
        surfaceDim = if (dark) background else card,
        error = if (dark) Color(0xFFFFB4AB) else Color(0xFFB3261E),
        onError = if (dark) Color(0xFF601410) else Color.White,
        errorContainer = if (dark) Color(0xFF601410) else Color(0xFFFFDAD6),
        onErrorContainer = if (dark) Color(0xFFFFDAD6) else Color(0xFF410002),
        scrim = Color.Black,
    )
}

private val schemes = palettes.mapValues { (_, pair) -> pair.first.scheme(false) to pair.second.scheme(true) }

fun homePhotoColorScheme(palette: AppPalette, dark: Boolean): ColorScheme =
    schemes.getValue(palette).let { if (dark) it.second else it.first }
