package com.chochocho.homephotoclient.ui.theme

import androidx.compose.ui.graphics.Color

/** 사진 위 오버레이는 테마와 무관하게 유지한다. 일반 UI는 colorScheme을 사용한다. */
object HomePhotoColors {
    // 뷰어·썸네일 오버레이 전용 (테마와 무관하게 항상 사진 위에 얹힘)
    val ViewerBackground = Color.Black
    val OverlayText = Color.White
    val OverlayTextDim = Color(0xFFBDBDBD)
    val OverlayWarning = Color(0xFFFFD54F)
    val Scrim = Color(0x99000000)
}
