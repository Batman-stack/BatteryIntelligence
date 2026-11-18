package com.batteryintelligence.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BatteryTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 52.sp,
        lineHeight = 58.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.75).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum"
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum"
    )
)
