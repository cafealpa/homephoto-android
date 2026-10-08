package com.chochocho.homephotoclient.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.chochocho.homephotoclient.data.AppColorMode
import com.chochocho.homephotoclient.data.AppPalette
import com.chochocho.homephotoclient.ui.theme.homePhotoColorScheme
import org.junit.Assert.*
import org.junit.Test

class AppearanceTest {
    @Test fun oldOrUnknownPreferencesUseSafeDefaults() {
        assertEquals(AppPalette.PEACH, AppPalette.fromStored(null))
        assertEquals(AppPalette.PEACH, AppPalette.fromStored("retired-palette"))
        assertEquals(AppColorMode.SYSTEM, AppColorMode.fromStored(null))
        assertEquals(AppColorMode.SYSTEM, AppColorMode.fromStored("unknown"))
        AppPalette.entries.forEach { assertEquals(it, AppPalette.fromStored(it.id)) }
        AppColorMode.entries.forEach { assertEquals(it, AppColorMode.fromStored(it.id)) }
    }

    @Test fun explicitModeOverridesSystemAndSystemModeFollowsBothAppearances() {
        assertFalse(AppColorMode.SYSTEM.isDark(false))
        assertTrue(AppColorMode.SYSTEM.isDark(true))
        listOf(false, true).forEach { system ->
            assertFalse(AppColorMode.LIGHT.isDark(system))
            assertTrue(AppColorMode.DARK.isDark(system))
        }
    }

    @Test fun allTwelveSchemesKeepReadableTextAndActions() {
        AppPalette.entries.forEach { palette -> listOf(false, true).forEach { dark ->
            val c = homePhotoColorScheme(palette, dark)
            val surfaces = listOf(c.background, c.surfaceContainerLow, c.surfaceContainer,
                c.surfaceContainerHigh, c.surfaceContainerHighest)
            surfaces.forEach { surface ->
                readable(palette, dark, "본문", c.onSurface, surface)
                readable(palette, dark, "보조 글자", c.onSurfaceVariant, surface)
                readable(palette, dark, "링크", c.primary, surface)
                readable(palette, dark, "오류", c.error, surface)
            }
            readable(palette, dark, "버튼", c.onPrimary, c.primary)
            readable(palette, dark, "선택", c.onPrimaryContainer, c.primaryContainer)
            readable(palette, dark, "오류 카드", c.onErrorContainer, c.errorContainer)
            readable(palette, dark, "스낵바", c.inverseOnSurface, c.inverseSurface)
            readable(palette, dark, "스낵바 동작", c.inversePrimary, c.inverseSurface)
        } }
    }

    private fun readable(palette: AppPalette, dark: Boolean, role: String, text: Color, background: Color) {
        val a = text.luminance(); val b = background.luminance()
        val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        assertTrue("$palette dark=$dark $role 대비=$ratio", ratio >= 4.5f)
    }
}
