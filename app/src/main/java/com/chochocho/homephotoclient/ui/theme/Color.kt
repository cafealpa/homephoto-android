package com.chochocho.homephotoclient.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 앱 팔레트. **다크 전용**이다 — 라이트 스킴은 다크 리디자인 채택 시 제거했다.
 * 사진이 주인공이므로 크롬(배경·바·카드)은 채도 없는 중립색으로, 포인트 색은 하나(틸)만 쓴다.
 * 화면 코드에서 Color(0x...)를 직접 쓰지 말고 MaterialTheme.colorScheme.* 역할 토큰을 쓴다.
 */
object HomePhotoColors {
    // 포인트(틸) — 버튼, 선택 탭·칩, 진행률, 링크
    val Teal10 = Color(0xFF002020)                   // 틸 버튼 위 글자
    val Teal30 = Color(0xFF004F51)
    val Teal80 = Color(0xFF4FD8DA)                   // primary
    val Teal90 = Color(0xFF6FF6F8)

    // 보조(중립 틸 그레이)
    val Neutral10 = Color(0xFF051F1F)
    val Neutral30 = Color(0xFF324B4B)
    val Neutral80 = Color(0xFFB0CCCC)
    val Neutral90 = Color(0xFFCCE8E8)

    // 표면(다크) — 2026-08-19 다크 리디자인 기준. 틸 기미를 뺀 중립 무채색으로,
    // 배경 → 카드는 아주 좁은 단계로만 올리고 경계선은 눈에 거의 띄지 않게 둔다.
    // 전체화면 뷰어만 순흑(ViewerBackground).
    val SurfaceDark = Color(0xFF0F0F11)              // 화면 배경
    val SurfaceContainerLowestDark = Color(0xFF0B0B0D)
    val SurfaceContainerLowDark = Color(0xFF141417)
    val SurfaceContainerDark = Color(0xFF17171A)     // 카드 (백업 요약, 실패 이력)
    val SurfaceContainerHighDark = Color(0xFF1D1D21)
    val SurfaceContainerHighestDark = Color(0xFF232326)
    val SurfaceVariantDark = Color(0xFF17171A)
    val OnSurfaceDark = Color(0xFFE1E3E3)            // 본문
    val OnSurfaceVariantDark = Color(0xFFBEC9C9)     // 보조 텍스트, 비선택 탭
    val OutlineDark = Color(0xFF232326)              // 경계선 · 구분선 · 칩 테두리
    val OutlineVariantDark = Color(0xFF1D1D21)

    // 오류 — 백업 실패 건수, 실패 사유
    val Error10 = Color(0xFF410002)
    val Error30 = Color(0xFF93000A)
    val Error80 = Color(0xFFFFB4AB)                  // error (다크에서 실제로 보이는 색)
    val Error90 = Color(0xFFFFDAD6)

    // 뷰어·썸네일 오버레이 전용 (테마와 무관하게 항상 사진 위에 얹힘)
    val ViewerBackground = Color.Black
    val OverlayText = Color.White
    val OverlayTextDim = Color(0xFFBDBDBD)
    val OverlayWarning = Color(0xFFFFD54F)
    val Scrim = Color(0x99000000)
}
