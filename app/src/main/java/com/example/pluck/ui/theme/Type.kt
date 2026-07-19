package com.example.pluck.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val PluckFont = FontFamily.Default

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 64.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-0.25).sp),
    displaySmall = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.25.sp),
    labelSmall = TextStyle(fontFamily = PluckFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp)
)
