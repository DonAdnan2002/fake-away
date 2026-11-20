package com.fakeaway.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FakeAwayTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    )
)
