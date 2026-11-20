package com.fakeaway.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.fakeaway.app.core.domain.AppLanguage

private val FakeAwayDarkColorScheme = darkColorScheme(
    primary = ForensicCyan,
    onPrimary = MidnightBg,
    secondary = ForensicReal,
    onSecondary = MidnightBg,
    tertiary = ForensicSuspicious,
    error = ForensicFake,
    background = MidnightBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = MidnightBorder
)

@Composable
fun FakeAwayTheme(
    language: AppLanguage = AppLanguage.ARABIC,
    content: @Composable () -> Unit
) {
    val direction = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = FakeAwayDarkColorScheme,
            typography = FakeAwayTypography,
            content = content
        )
    }
}
