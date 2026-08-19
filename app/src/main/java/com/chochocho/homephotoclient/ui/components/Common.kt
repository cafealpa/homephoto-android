package com.chochocho.homephotoclient.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chochocho.homephotoclient.ui.theme.HomePhotoSpacing

/**
 * 1c 확장 공통 컴포넌트. 색·크기는 `docs/design-1c.md` 실측값을 따른다.
 * 화면 코드에서 색을 직접 쓰지 않도록 여기서 colorScheme 역할 토큰으로 감싼다.
 */

/** 카드: 배경 surfaceContainer(#17171A) + 1px outline(#232326) + 반경 12dp + 패딩 16dp. */
@Composable
fun HomePhotoCard(
    modifier: Modifier = Modifier,
    verticalGap: androidx.compose.ui.unit.Dp = HomePhotoSpacing.tight,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(HomePhotoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(verticalGap),
        content = content,
    )
}

/**
 * 화면 제목 행. 제목은 headlineSmall(24/32), 오른쪽 동작은 틸 텍스트 버튼.
 * 패딩은 1c 실측값 `4px 16px 8px`.
 */
@Composable
fun ScreenTitleRow(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 월 칩 한 개. 선택 시 테두리·글자 모두 틸 + Medium, 비선택은 outline 테두리 + 보조 글자색.
 * 반경 8dp, 패딩 `8px 14px`.
 */
@Composable
fun MonthChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        ),
        color = fg,
        modifier = modifier
            .border(1.dp, border, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** 월 칩 줄. 좌우 스크롤되며 간격 8dp, 패딩 `0 16px 12px`. */
@Composable
fun MonthChipRow(
    months: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    monthLabel: (String) -> String = { it },
) {
    if (months.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item),
    ) {
        items(months.size) { i ->
            val m = months[i]
            MonthChip(label = monthLabel(m), selected = m == selected, onClick = { onSelect(m) })
        }
    }
}

/** 오류 안내 블록 — 제목 + 설명 + 다시 시도. 타임라인·인물이 공유한다. */
@Composable
fun ErrorBlock(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "다시 시도",
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PrimaryActionButton(retryLabel, onClick = onRetry)
    }
}

private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)

/** 채움 버튼 — 틸 배경 + #002020 글자, 반경 8dp, 패딩 `14px 24px`. */
@Composable
fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonPadding,
        modifier = modifier,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** 테두리 버튼 — 틸 테두리 + 틸 글자, 반경 8dp, 패딩 `14px 24px`. */
@Composable
fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 하단 내비게이션 (1c 확장). 아이콘 없이 라벨만, 선택 항목은 틸 + Medium.
 * 위쪽 1px 경계선, 아래는 시스템 제스처 영역만큼 띄운다.
 *
 * 원 디자인은 `rgba(15,15,17,.72)` + `backdrop-filter: blur(20px)`로 사진이 바 뒤로
 * 흐려 보이는 프로스티드 효과지만, 여기서는 **불투명 화면색**을 쓴다 —
 * Scaffold 가 바 높이만큼 본문 영역을 줄이므로 바 뒤로 지나가는 사진이 없고,
 * 같은 색 배경 위의 72% 반투명은 불투명과 화면상 구분되지 않는다.
 * 나중에 사진이 바 뒤로 스크롤되게 바꾼다면 그때 반투명 + 블러를 함께 도입한다.
 */
@Composable
fun HomePhotoBottomNav(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}
