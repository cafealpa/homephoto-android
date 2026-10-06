package com.chochocho.homephotoclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.gson.Gson
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.chochocho.homephotoclient.R
import com.chochocho.homephotoclient.data.*
import com.chochocho.homephotoclient.ui.components.ErrorBlock
import com.chochocho.homephotoclient.ui.theme.HomePhotoColors
import com.chochocho.homephotoclient.ui.theme.HomePhotoSpacing
import kotlinx.coroutines.CancellationException
import java.time.YearMonth

@Composable
fun HomeScreen(repository: SettingsRepository, onPhotos: () -> Unit, onPeople: () -> Unit, onBackup: () -> Unit) {
    val config by repository.settings.collectAsState(initial = null)
    val cfg = config ?: return
    val api = remember(cfg.serverUrl, cfg.internalServerUrl, cfg.apiKey) { repository.createApi(cfg) }
    var photos by remember(api) { mutableStateOf<List<AssetDto>>(emptyList()) }
    var people by remember(api) { mutableStateOf<List<ClusterDto>>(emptyList()) }
    var loading by remember(api) { mutableStateOf(true) }
    var error by remember(api) { mutableStateOf<String?>(null) }
    var peopleError by remember(api) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var selected by remember(api) { mutableStateOf<Int?>(null) }
    var person by remember(api) { mutableStateOf<ClusterDto?>(null) }
    LaunchedEffect(api, refresh) {
        loading = true
        error = null
        try { photos = api.assets(limit = 12).items }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.toFriendlyMessage() }
        finally { loading = false }
    }
    LaunchedEffect(api, refresh) {
        peopleError = null
        try { people = api.clusters() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { peopleError = e.toFriendlyMessage() }
    }
    var family by remember(api) { mutableStateOf<FamilyAlbumHome?>(null) }
    var familyError by remember(api) { mutableStateOf<String?>(null) }
    val albumSaver = remember { Saver<FamilyAlbumSummary?, String>(
        save = { Gson().toJson(it) }, restore = { Gson().fromJson(it, FamilyAlbumSummary::class.java) }) }
    var familyAlbum by rememberSaveable(cfg.serverUrl, cfg.apiKey, stateSaver = albumSaver) { mutableStateOf<FamilyAlbumSummary?>(null) }
    LaunchedEffect(api, refresh) {
        familyError = null
        try { family = api.familyAlbumsHome() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { familyError = if (e is retrofit2.HttpException && e.code() == 404) "서버를 업데이트하면 가족 앨범을 사용할 수 있어요." else e.toFriendlyMessage() }
    }
    familyAlbum?.let { album ->
        key(album.kind, album.date) { FamilyAlbumScreen(api, cfg, album, onBack = { familyAlbum = null; refresh++ }) }
        return
    }
    person?.let { cluster ->
        PersonDetailScreen(api, cfg, cluster, onBack = { person = null; refresh++ })
        return
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(HomePhotoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.spacious),
        ) {
            item {
                Column {
                    Text("우리 가족의 사진집", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("함께한 순간들", style = MaterialTheme.typography.headlineLarge)
                    Text("소중한 일상을 다시 만나보세요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (loading && photos.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { ErrorBlock("사진을 불러올 수 없어요", message, onRetry = { refresh++ }) } }
            if (!loading && error == null && photos.isEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(HomePhotoSpacing.spacious), verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item)) {
                        Text("우리의 첫 사진을 담아볼까요?", style = MaterialTheme.typography.titleLarge)
                        Text("백업을 시작하면 가족의 사진이 여기에 모여요.")
                        Button(onClick = onBackup) { Text("사진 백업 시작") }
                    }
                }
            }
            item {
                Text("이번 주 우리 가족", style = MaterialTheme.typography.titleLarge)
                familyError?.let { ErrorBlock("가족 앨범을 불러올 수 없어요", it, onRetry = { refresh++ }) }
                if (family == null && familyError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                family?.weekly?.forEach { album -> FamilyAlbumCard(album, cfg) { familyAlbum = album } }
            }
            family?.let { albums ->
                item {
                    Text("함께 찍은 하루", style = MaterialTheme.typography.titleLarge)
                    Text("여러 기기에서 담은 같은 날의 사진", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (albums.together.isEmpty()) Text("최근 30일 중 두 대 이상 기기의 사진이 있는 날이 여기에 모여요.")
                    albums.together.forEach { album -> FamilyAlbumCard(album, cfg) { familyAlbum = album } }
                }
                if (albums.saved.isNotEmpty()) item {
                    Text("완성한 가족 앨범", style = MaterialTheme.typography.titleLarge)
                    albums.saved.forEach { album -> FamilyAlbumCard(album, cfg) { familyAlbum = album } }
                }
            }
            if (photos.isNotEmpty()) item {
                val hero = photos.firstOrNull { it.mediaType == "PHOTO" } ?: photos.first()
                Box(Modifier.fillMaxWidth().aspectRatio(1.05f).clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainer).clickable { selected = photos.indexOf(hero) }) {
                    AsyncImage(
                        ImageRequest.Builder(LocalContext.current).data("${cfg.serverUrl}/api/v1/assets/${hero.id}/thumb?size=1600")
                            .setHeader("X-Api-Key", cfg.apiKey).build(),
                        contentDescription = hero.originalFilename ?: "최근 가족 사진", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(HomePhotoColors.Scrim.copy(alpha = 0f), HomePhotoColors.Scrim))))
                    Column(Modifier.align(Alignment.BottomStart).padding(HomePhotoSpacing.spacious)) {
                        Text(hero.yearMonth?.let(::homeMonthLabel) ?: "최근 사진", color = HomePhotoColors.OverlayTextDim, style = MaterialTheme.typography.labelLarge)
                        Text("사진으로 남긴\n우리의 일상", color = HomePhotoColors.OverlayText, style = MaterialTheme.typography.headlineMedium)
                        Text("눌러서 크게 보기", color = HomePhotoColors.OverlayText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.section)) {
                    DiscoverySectionTitle("사진 속 우리", "모두 보기", onPeople)
                    when {
                        peopleError != null -> ErrorBlock("인물을 불러올 수 없어요", peopleError!!, onRetry = { refresh++ })
                        people.isEmpty() -> Text("얼굴 분석이 끝나면 가족의 사진을 인물별로 볼 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.screen)) {
                            items(people.take(10), key = { it.clusterId }) { cluster ->
                                Box(Modifier.width(HomePhotoSpacing.avatar)) {
                                    ClusterCell(cluster, cfg.serverUrl, cfg.apiKey) { person = cluster }
                                }
                            }
                        }
                    }
                }
            }
            if (photos.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.section)) {
                    DiscoverySectionTitle("최근에 담은 순간", "전체 사진", onPhotos)
                    photos.take(6).chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item)) {
                            row.forEach { asset -> ThumbCell(asset, cfg.serverUrl, cfg.apiKey, { selected = photos.indexOf(asset) }, Modifier.weight(1f).clip(MaterialTheme.shapes.medium)) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_backup), contentDescription = null)
                    Spacer(Modifier.width(HomePhotoSpacing.item))
                    Text("백업 상태 확인")
                }
            }
        }
        selected?.let { index -> FullScreenViewer(photos, index, cfg.serverUrl, cfg.apiKey, onClose = { selected = null }) }
    }
}

internal fun homeMonthLabel(value: String): String = YearMonth.parse(value).let { "${it.year}년 ${it.monthValue}월" }

@Composable
internal fun DiscoverySectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun FamilyAlbumCard(album: FamilyAlbumSummary, cfg: AppSettings, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = HomePhotoSpacing.item)) {
        Row(Modifier.padding(HomePhotoSpacing.section), verticalAlignment = Alignment.CenterVertically) {
            album.coverAssetId?.let { id ->
                AsyncImage(ImageRequest.Builder(LocalContext.current).data("${cfg.serverUrl}/api/v1/assets/$id/thumb?size=400")
                    .setHeader("X-Api-Key", cfg.apiKey).build(), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.size(HomePhotoSpacing.avatar).clip(MaterialTheme.shapes.medium))
                Spacer(Modifier.width(HomePhotoSpacing.section))
            }
            Column(Modifier.weight(1f)) {
                Text(album.title, style = MaterialTheme.typography.titleMedium)
                Text("${album.date} · 후보 ${album.photoCount}장 · 기기 ${album.deviceCount}대", style = MaterialTheme.typography.bodySmall)
                Text(if (album.albumId == null) "사진 골라 완성하기 →" else "저장한 앨범 보기 →", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
