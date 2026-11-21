package com.example.agenticrag.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = AppColors.Navy),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = AppColors.Navy),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.Navy),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = AppColors.Navy),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.Navy),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, color = AppColors.Navy, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = AppColors.NavyMuted, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.Cream),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = AppColors.Navy),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

private val AppColorScheme = lightColorScheme(
    primary = AppColors.Navy,
    onPrimary = AppColors.Cream,
    primaryContainer = AppColors.Indigo,
    onPrimaryContainer = AppColors.Cream,
    secondary = AppColors.Indigo,
    onSecondary = AppColors.Cream,
    secondaryContainer = AppColors.Lavender,
    onSecondaryContainer = AppColors.Navy,
    tertiary = AppColors.Lavender,
    onTertiary = AppColors.Navy,
    background = AppColors.Cream,
    onBackground = AppColors.Navy,
    surface = AppColors.Cream,
    onSurface = AppColors.Navy,
    surfaceVariant = AppColors.Lavender,
    onSurfaceVariant = AppColors.Navy,
    outline = AppColors.Indigo,
    outlineVariant = AppColors.Lavender,
    error = AppColors.Navy,
    onError = AppColors.Cream,
)

@Composable
fun AgenticRAGTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
