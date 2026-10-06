package com.chochocho.homephotoclient.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.chochocho.homephotoclient.data.AppSettings
import com.chochocho.homephotoclient.data.AssetDto
import com.chochocho.homephotoclient.data.SettingsRepository
import com.chochocho.homephotoclient.ui.components.ErrorBlock
import com.chochocho.homephotoclient.ui.components.MonthChipRow
import com.chochocho.homephotoclient.ui.components.ScreenTitleRow
import com.chochocho.homephotoclient.ui.theme.HomePhotoSpacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal sealed interface Cell {
    val yearMonth: String

    data class Header(override val yearMonth: String) : Cell

    data class Photo(val asset: AssetDto, val index: Int, override val yearMonth: String) : Cell

    /**
     * 1c 확장의 대표 타일. 대표 1장(2×2 크기) + 옆에 작은 셀 4장(2×2 배치)을
     * 한 덩어리로 묶은 항목. `LazyVerticalGrid`가 행 span을 지원하지 않아
     * 4열 전체를 차지하는 항목 하나로 만들어 안에서 직접 배치한다.
     */
    data class Featured(
        val assets: List<AssetDto>,
        val startIndex: Int,
        override val yearMonth: String,
    ) : Cell
}

/** 대표 타일이 소비하는 사진 수 — 대표 1 + 작은 셀 4. */
internal const val FEATURED_TAKE = 5

/** 대표 타일은 4열 배치에서만 성립한다(2×2 + 2×2). */
internal const val FEATURED_COLUMNS = 4

@Composable
fun TimelineScreen(repository: SettingsRepository) {
    var config by remember { mutableStateOf<AppSettings?>(null) }
    LaunchedEffect(Unit) { config = repository.settings.first() }
    val cfg = config ?: return

    var selectedYear by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    val monthState = remember(cfg.serverUrl, cfg.internalServerUrl, cfg.apiKey) {
        TimelineMonthsState(repository.createApi(cfg))
    }
    LaunchedEffect(monthState) { monthState.load() }

    val period = TimelinePeriodFilter(monthState.months, selectedYear, selectedMonth)
    // 주의: Retrofit 프록시 객체를 remember의 key로 쓰면 안 된다 (equals가 항상 false)
    val state = remember(cfg.serverUrl, cfg.internalServerUrl, cfg.apiKey, period.yearMonths) {
        AssetListState(repository.createApi(cfg), yearMonths = period.yearMonths)
    }
    val scope = rememberCoroutineScope()
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var columns by remember { mutableIntStateOf(FEATURED_COLUMNS) }

    LaunchedEffect(state) { state.loadMore() }

    val cells = remember(state.items, columns) { buildCells(state.items, columns) }
    val gridState = rememberLazyGridState()

    // cells는 재구성마다 새 목록이므로 장기 실행 중인 스크롤 감시에는 최신 개수를 전달한다.
    val cellCount by rememberUpdatedState(cells.size)
    LaunchedEffect(selectedYear, selectedMonth) { gridState.scrollToItem(0) }
    LaunchedEffect(gridState, state) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(last, cellCount, !state.loading && state.error == null)
        }.collect { (lastVisible, itemCount, ready) ->
            if (ready && itemCount > 0 && lastVisible >= itemCount - 40) state.loadMore()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTitleRow(
                title = "사진",
                actionLabel = "새로고침",
                onAction = {
                    state.reset()
                    scope.launch { state.loadMore() }
                    scope.launch { monthState.load() }
                },
            )

            val error = state.error
            if (state.items.isEmpty() && error != null && !state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorBlock(
                        title = "사진을 불러올 수 없어요",
                        message = error,
                        onRetry = {
                            state.reset()
                            scope.launch { state.loadMore() }
                        },
                    )
                }
                return@Column
            }

            if (monthState.loading) Text("월 목록을 불러오는 중…",
                modifier = Modifier.padding(horizontal = HomePhotoSpacing.screen),
                style = MaterialTheme.typography.bodySmall)
            monthState.error?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = HomePhotoSpacing.screen),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("월 목록: $message", Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { scope.launch { monthState.load() } }) { Text("재시도") }
                }
            }
            MonthChipRow(
                months = listOf("전체 연도") + period.years,
                selected = selectedYear ?: "전체 연도",
                onSelect = { year ->
                    val nextYear = year.takeUnless { it == "전체 연도" }
                    if (nextYear != selectedYear) {
                        selectedIndex = null
                        selectedYear = nextYear
                        selectedMonth = null
                    }
                },
                monthLabel = { if (it == "전체 연도") it else "${it}년" },
            )
            if (selectedYear != null) {
                MonthChipRow(
                    months = listOf("전체 월") + period.months,
                    selected = selectedMonth ?: "전체 월",
                    onSelect = { month ->
                        selectedIndex = null
                        selectedMonth = month.takeUnless { it == "전체 월" }
                    },
                    monthLabel = { if (it == "전체 월") it else "${it.toInt()}월" },
                )
            }

            if (error != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HomePhotoSpacing.screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { scope.launch { state.loadMore() } }) { Text("재시도") }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 두 손가락 핀치로 그리드 열 수 조절 (2~8열). 한 손가락 스크롤과 충돌 없음
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            var zoomAccum = 1f
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.none { it.pressed }) break
                                if (event.changes.count { it.pressed } >= 2) {
                                    zoomAccum *= event.calculateZoom()
                                    if (zoomAccum > 1.2f) {
                                        if (columns > 2) columns--
                                        zoomAccum = 1f
                                    } else if (zoomAccum < 1f / 1.2f) {
                                        if (columns < 8) columns++
                                        zoomAccum = 1f
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.grid),
                    horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.grid),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = cells.size,
                        key = { i ->
                            when (val c = cells[i]) {
                                is Cell.Header -> "h:${c.yearMonth}"
                                is Cell.Photo -> c.asset.id
                                is Cell.Featured -> "f:${c.assets.first().id}"
                            }
                        },
                        span = { i ->
                            if (cells[i] is Cell.Photo) GridItemSpan(1) else GridItemSpan(maxLineSpan)
                        },
                    ) { i ->
                        when (val cell = cells[i]) {
                            is Cell.Header -> Text(
                                text = formatMonth(cell.yearMonth),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                            )

                            is Cell.Photo -> ThumbCell(
                                asset = cell.asset,
                                baseUrl = cfg.serverUrl,
                                apiKey = cfg.apiKey,
                                onClick = { selectedIndex = cell.index },
                            )

                            is Cell.Featured -> FeaturedTile(
                                cell = cell,
                                baseUrl = cfg.serverUrl,
                                apiKey = cfg.apiKey,
                                onClick = { selectedIndex = it },
                            )
                        }
                    }
                    if (state.loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(HomePhotoSpacing.screen),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }

        selectedIndex?.let { startIndex ->
            FullScreenViewer(
                items = state.items,
                startIndex = startIndex,
                baseUrl = cfg.serverUrl,
                apiKey = cfg.apiKey,
                onClose = { selectedIndex = null },
                onNearEnd = { scope.launch { state.loadMore() } },
                onDelete = { asset ->
                    scope.launch {
                        try {
                            state.api.deleteAsset(asset.id)
                            state.removeById(asset.id)
                        } catch (e: Exception) {
                            android.util.Log.e("Timeline", "delete failed", e)
                        }
                        selectedIndex = null // 인덱스가 밀리므로 뷰어를 닫는다
                    }
                },
            )
        }
    }
}

/**
 * 대표 타일: 왼쪽 절반에 대표 1장(정사각), 오른쪽 절반에 작은 셀 4장(2×2).
 * 4열 그리드에서 두 덩어리의 높이가 정확히 같아진다 —
 * 대표는 (가로 1/2)의 정사각, 작은 셀은 (가로 1/4) 정사각 두 줄.
 */
@Composable
private fun FeaturedTile(
    cell: Cell.Featured,
    baseUrl: String,
    apiKey: String,
    onClick: (Int) -> Unit,
) {
    val gap = HomePhotoSpacing.grid
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        ThumbCell(
            asset = cell.assets[0],
            baseUrl = baseUrl,
            apiKey = apiKey,
            onClick = { onClick(cell.startIndex) },
            modifier = Modifier.weight(2f),
        )
        Column(
            modifier = Modifier.weight(2f),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until 2) {
                        val offset = 1 + row * 2 + col
                        ThumbCell(
                            asset = cell.assets[offset],
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            onClick = { onClick(cell.startIndex + offset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 사진 목록을 그리드 항목으로 변환한다. 월이 바뀌면 헤더를 넣고,
 * 4열일 때는 **첫 월의 앞 5장**을 대표 타일 하나로 묶는다.
 */
internal fun buildCells(items: List<AssetDto>, columns: Int): List<Cell> = buildList {
    var currentMonth: String? = null
    var featuredUsed = false
    var i = 0
    while (i < items.size) {
        val asset = items[i]
        val ym = asset.yearMonth ?: "기타"
        if (ym != currentMonth) {
            add(Cell.Header(ym))
            currentMonth = ym
        }
        val canFeature = !featuredUsed &&
            columns == FEATURED_COLUMNS &&
            i + FEATURED_TAKE <= items.size &&
            items.subList(i, i + FEATURED_TAKE).all { (it.yearMonth ?: "기타") == ym }
        if (canFeature) {
            add(Cell.Featured(items.subList(i, i + FEATURED_TAKE).toList(), i, ym))
            featuredUsed = true
            i += FEATURED_TAKE
        } else {
            add(Cell.Photo(asset, i, ym))
            i++
        }
    }
}

private fun formatMonth(yearMonth: String): String {
    val parts = yearMonth.split("-")
    return if (parts.size == 2) "${parts[0]}년 ${parts[1].trimStart('0')}월" else yearMonth
}
