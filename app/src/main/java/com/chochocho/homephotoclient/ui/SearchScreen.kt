package com.chochocho.homephotoclient.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import com.chochocho.homephotoclient.R
import com.chochocho.homephotoclient.data.*
import com.chochocho.homephotoclient.ui.components.ErrorBlock
import com.chochocho.homephotoclient.ui.theme.HomePhotoSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class DiscoveryState(private val api: HomePhotoApi, val query: PhotoQuery) {
    var items by mutableStateOf<List<AssetDto>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var indexedPhotos by mutableStateOf<Long?>(null)
        private set
    var canLoadMore by mutableStateOf(true)
        private set
    private var cursor: String? = null

    suspend fun loadMore() {
        if (loading || !canLoadMore) return
        loading = true
        error = null
        try {
            val page = api.discover(query, cursor)
            items = (items + page.items).distinctBy { it.id }
            cursor = page.nextCursor
            canLoadMore = cursor != null
            indexedPhotos = page.indexedPhotos
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.toSearchMessage()
        } finally {
            loading = false
        }
    }
}

@Composable
fun SearchScreen(repository: SettingsRepository, onPeople: () -> Unit) {
    val config by repository.settings.collectAsState(initial = null)
    val cfg = config ?: return
    val api = remember(cfg.serverUrl, cfg.apiKey) { ApiFactory.create(cfg.serverUrl, cfg.apiKey) }
    var draft by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var periodOpen by remember { mutableStateOf(false) }
    var months by remember(api) { mutableStateOf<List<MonthDto>>(emptyList()) }
    var monthsLoading by remember(api) { mutableStateOf(false) }
    var monthsError by remember(api) { mutableStateOf<String?>(null) }
    var monthsRevision by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val query = PhotoQuery(submitted, month)
    val state = remember(cfg.serverUrl, cfg.apiKey, query, revision) { DiscoveryState(api, query) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()
    val validation = PhotoQuery(draft).validationMessage
    fun submit() {
        if (validation != null) return
        submitted = draft.trim()
        revision++
        keyboard?.hide()
    }
    LaunchedEffect(state) {
        selectedIndex = null
        gridState.scrollToItem(0)
        state.loadMore()
    }
    LaunchedEffect(api, periodOpen, monthsRevision) {
        if (!periodOpen) return@LaunchedEffect
        monthsLoading = true
        monthsError = null
        try { months = api.months() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { monthsError = e.toFriendlyMessage() }
        finally { monthsLoading = false }
    }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), state = gridState,
            contentPadding = PaddingValues(HomePhotoSpacing.screen),
            horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item),
            verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.section)) {
                    Text("다시 보고 싶은 순간", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("어떤 사진을 찾나요?", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        label = { Text("장면을 설명해 주세요") }, placeholder = { Text("바닷가에서 찍은 가족사진") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                        trailingIcon = { if (draft.isNotEmpty()) TextButton(onClick = { draft = ""; submitted = ""; revision++ }) { Text("지우기") } },
                        singleLine = true, isError = validation != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                    )
                    validation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { periodOpen = true }, modifier = Modifier.weight(1f)) {
                            Text(month?.let(::homeMonthLabel) ?: "전체 기간")
                        }
                        Button(onClick = { submit() }, enabled = validation == null) { Text("검색") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item)) {
                        BrowseCard("인물별", "가족 얼굴로 찾기", R.drawable.ic_people, onPeople, Modifier.weight(1f))
                        BrowseCard("날짜별", "촬영한 달로 찾기", R.drawable.ic_calendar, { periodOpen = true }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (query.semantic) "검색 결과" else if (month != null) "${homeMonthLabel(month!!)} 사진" else "최근 사진",
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                        )
                        if (query.semantic || month != null) TextButton(onClick = { draft = ""; submitted = ""; month = null; revision++ }) { Text("초기화") }
                    }
                    if (query.semantic) Text(
                        "‘${query.keyword}’와 비슷한 사진을 관련도순으로 최대 24장 보여드려요.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorBlock("사진을 불러올 수 없어요", message, onRetry = { scope.launch { state.loadMore() } })
            } }
            if (!state.loading && state.error == null && state.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(vertical = HomePhotoSpacing.spacious), verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item)) {
                    Text(if (state.indexedPhotos == 0L) "아직 검색할 사진이 준비되지 않았어요" else "표시할 사진이 없어요", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (query.semantic) "검색 문장을 바꾸거나 기간을 넓혀보세요. 사진 분석이 진행 중이면 잠시 후 다시 검색해 주세요."
                        else "다른 달을 선택하거나 사진을 먼저 백업해 주세요.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.items.size, key = { state.items[it].id }) { index ->
                ThumbCell(state.items[index], cfg.serverUrl, cfg.apiKey, { selectedIndex = index }, Modifier.clip(MaterialTheme.shapes.medium))
            }
            if (state.canLoadMore && state.items.isNotEmpty() && state.error == null) item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = { scope.launch { state.loadMore() } }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("더 보기") }
            }
        }
        selectedIndex?.let { index ->
            FullScreenViewer(state.items, index, cfg.serverUrl, cfg.apiKey, onClose = { selectedIndex = null },
                onNearEnd = { if (state.error == null) scope.launch { state.loadMore() } })
        }
    }
    if (periodOpen) AlertDialog(
        onDismissRequest = { periodOpen = false },
        title = { Text("촬영 기간") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.tight)) {
                item { TextButton(onClick = { month = null; periodOpen = false }) { Text("전체 기간") } }
                if (monthsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                monthsError?.let { message -> item { ErrorBlock("기간을 불러올 수 없어요", message, onRetry = { monthsRevision++ }) } }
                if (!monthsLoading && monthsError == null && months.isEmpty()) item { Text("아직 저장된 사진이 없어요.") }
                items(months, key = { it.yearMonth }) { item ->
                    TextButton(onClick = { month = item.yearMonth; periodOpen = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("${homeMonthLabel(item.yearMonth)} · ${item.count}개")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { periodOpen = false }) { Text("닫기") } },
    )
}

@Composable
private fun BrowseCard(title: String, subtitle: String, icon: Int, onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(HomePhotoSpacing.screen), verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item)) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
