package com.batteryintelligence.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.batteryintelligence.domain.model.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = FreshEmerald,
    onPrimary = PureWhite,
    primaryContainer = SoftMintGreen,
    onPrimaryContainer = ForestEmeraldDark,
    secondary = DeepEmeraldText,
    onSecondary = PureWhite,
    secondaryContainer = SoftMintGreen,
    onSecondaryContainer = ForestEmeraldDark,
    tertiary = TealFastText,
    onTertiary = PureWhite,
    tertiaryContainer = TealFastContainer,
    onTertiaryContainer = Color(0xFF134E4A),
    background = PureWhite,
    onBackground = DeepCharcoal,
    surface = SubtleOffWhite,
    onSurface = DeepCharcoal,
    surfaceVariant = NeutralCoolGray,
    onSurfaceVariant = CoolSlate,
    surfaceContainerLow = SubtleOffWhite,
    surfaceContainer = NeutralCoolGray,
    outline = HairlineCoolBorder,
    outlineVariant = HairlineCoolBorder,
    error = AlertRedText,
    onError = PureWhite,
    errorContainer = AlertRedContainer,
    onErrorContainer = AlertRedText
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = Color(0xFF052E16),
    primaryContainer = DarkMintContainer,
    onPrimaryContainer = DarkEmeraldText,
    secondary = DarkEmeraldText,
    onSecondary = Color(0xFF052E16),
    secondaryContainer = DarkMintContainer,
    onSecondaryContainer = DarkEmeraldText,
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = Color(0xFF134E4A),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = DarkObsidian,
    onBackground = DarkTextPrimary,
    surface = DarkPineSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSlateCard,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLow = DarkPineSurface,
    surfaceContainer = DarkSlateCard,
    outline = DarkHairline,
    outlineVariant = DarkHairline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA)
)

val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun BatteryIntelligenceTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val semanticColors = if (useDark) DarkBatterySemanticColors else LightBatterySemanticColors

    val reducedMotion = remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        }.getOrDefault(1.0f)
        scale <= 0.0f
    }

    CompositionLocalProvider(
        LocalBatterySemanticColors provides semanticColors,
        LocalReducedMotion provides reducedMotion,
        LocalHapticsEnabled provides hapticsEnabled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BatteryTypography,
            content = content
        )
    }
}
