package com.batteryintelligence.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light-first clinical emerald & pure white palette (Section 9)
val PureWhite = Color(0xFFFFFFFF)
val SubtleOffWhite = Color(0xFFF7FAF8)
val NeutralCoolGray = Color(0xFFF1F5F9)
val HairlineCoolBorder = Color(0xFFE2E8F0)

val FreshEmerald = Color(0xFF16A34A)
val DeepEmeraldText = Color(0xFF15803D) // WCAG AA+ (>= 4.68:1) on White & Mint
val ForestEmeraldDark = Color(0xFF14532D)
val SoftMintGreen = Color(0xFFE8F5EE)
val MintHairlineBorder = Color(0xFFBBF7D0)

val DeepCharcoal = Color(0xFF111827)
val CoolSlate = Color(0xFF475569)
val MutedSlate = Color(0xFF64748B)

val AmberWarningText = Color(0xFFB45309)
val AmberWarningContainer = Color(0xFFFEF3C7)
val AmberStroke = Color(0xFFD97706)

val AlertRedText = Color(0xFFB91C1C)
val AlertRedContainer = Color(0xFFFEE2E2)
val AlertRedStroke = Color(0xFFDC2626)

val TealFastText = Color(0xFF0F766E)
val TealFastContainer = Color(0xFFCCFBF1)

// Optional Dark Mode counterpart
val DarkObsidian = Color(0xFF0B110E)
val DarkPineSurface = Color(0xFF111916)
val DarkSlateCard = Color(0xFF1A2420)
val DarkMintContainer = Color(0xFF132E20)
val DarkHairline = Color(0xFF27352E)
val DarkEmeraldPrimary = Color(0xFF22C55E)
val DarkEmeraldText = Color(0xFF4ADE80)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)

@Immutable
data class BatterySemanticColors(
    val emeraldText: Color,
    val emeraldStroke: Color,
    val mintSurface: Color,
    val mintBorder: Color,
    val cardBorder: Color,
    val elevatedCanvas: Color,
    val warmText: Color,
    val warmContainer: Color,
    val warmStroke: Color,
    val alertText: Color,
    val alertContainer: Color,
    val alertStroke: Color,
    val fastTealText: Color,
    val fastTealContainer: Color,
    val mutedCaption: Color
)

val LightBatterySemanticColors = BatterySemanticColors(
    emeraldText = DeepEmeraldText,
    emeraldStroke = FreshEmerald,
    mintSurface = SoftMintGreen,
    mintBorder = MintHairlineBorder,
    cardBorder = HairlineCoolBorder,
    elevatedCanvas = SubtleOffWhite,
    warmText = AmberWarningText,
    warmContainer = AmberWarningContainer,
    warmStroke = AmberStroke,
    alertText = AlertRedText,
    alertContainer = AlertRedContainer,
    alertStroke = AlertRedStroke,
    fastTealText = TealFastText,
    fastTealContainer = TealFastContainer,
    mutedCaption = MutedSlate
)

val DarkBatterySemanticColors = BatterySemanticColors(
    emeraldText = DarkEmeraldText,
    emeraldStroke = DarkEmeraldPrimary,
    mintSurface = DarkMintContainer,
    mintBorder = Color(0xFF1F4E35),
    cardBorder = DarkHairline,
    elevatedCanvas = DarkPineSurface,
    warmText = Color(0xFFFBBF24),
    warmContainer = Color(0xFF451A03),
    warmStroke = Color(0xFFF59E0B),
    alertText = Color(0xFFF87171),
    alertContainer = Color(0xFF450A0A),
    alertStroke = Color(0xFFEF4444),
    fastTealText = Color(0xFF2DD4BF),
    fastTealContainer = Color(0xFF134E4A),
    mutedCaption = DarkTextSecondary
)

val LocalBatterySemanticColors = staticCompositionLocalOf { LightBatterySemanticColors }
