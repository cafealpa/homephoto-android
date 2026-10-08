package com.chochocho.homephotoclient.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.isSystemInDarkTheme
import com.chochocho.homephotoclient.data.AppPalette
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** 모서리: 카드/다이얼로그 12dp, 칩·버튼 8dp, 썸네일 셀은 0(그리드 밀착). */
val HomePhotoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** 간격 토큰. 화면 여백 16, 섹션 사이 12, 항목 사이 8, 그리드 틈 2. */
object HomePhotoSpacing {
    val screen = 16.dp
    val section = 12.dp
    val item = 8.dp
    val tight = 4.dp
    val grid = 2.dp
    val spacious = 24.dp
    val avatar = 72.dp
    val touchTarget = 48.dp
    val swatch = 22.dp
    val hairline = 1.dp
}

/** 6가지 팔레트와 노멀/다크 모드. 사진 색감과 독립적인 고정 색상이다. */
@Composable
fun HomePhotoTheme(
    palette: AppPalette = AppPalette.PEACH,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = homePhotoColorScheme(palette, darkTheme),
        typography = HomePhotoTypography,
        shapes = HomePhotoShapes,
        content = content,
    )
}
