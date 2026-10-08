package com.chochocho.homephotoclient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chochocho.homephotoclient.data.*
import com.chochocho.homephotoclient.ui.components.ErrorBlock
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val albumPhotosSaver = Saver<List<AssetDto>, String>(
    save = { Gson().toJson(it) }, restore = { Gson().fromJson(it, Array<AssetDto>::class.java).toList() },
)

@Composable
internal fun FamilyAlbumScreen(api: HomePhotoApi, cfg: AppSettings, album: FamilyAlbumSummary,
                               onBack: () -> Unit) {
    var detail by remember { mutableStateOf<FamilyAlbumDetail?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable(stateSaver = albumPhotosSaver) { mutableStateOf(emptyList<AssetDto>()) }
    var revision by rememberSaveable { mutableIntStateOf(0) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var edited by rememberSaveable { mutableStateOf(false) }
    var candidates by remember { mutableStateOf(emptyList<AssetDto>()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var candidateLoaded by remember { mutableStateOf(false) }
    var candidateBusy by remember { mutableStateOf(false) }
    var candidateError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var exitDialog by remember { mutableStateOf(false) }
    var reloadDialog by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Int?>(null) }
    var savedMessage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun accept(value: FamilyAlbumDetail) {
        detail = value; title = value.summary.title; note = value.note; selected = value.selected
        revision = value.revision; initialized = true; edited = false
    }
    fun leave() { if (!busy) { if (edited) exitDialog = true else onBack() } }
    fun loadCandidates() {
        if (candidateBusy) return
        candidateBusy = true; candidateError = null
        scope.launch {
            try {
                val page = api.familyAlbumCandidates(album.kind, album.date, cursor)
                candidates = (candidates + page.items).distinctBy { it.id }
                cursor = page.nextCursor; candidateLoaded = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { candidateError = e.toFriendlyMessage() }
            finally { candidateBusy = false }
        }
    }
    LaunchedEffect(cfg.serverUrl, cfg.internalServerUrl, cfg.apiKey, refresh) {
        busy = true; error = null
        try {
            val value = api.familyAlbum(album.kind, album.date)
            detail = value
            if (!initialized) accept(value)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.toFriendlyMessage() }
        finally { busy = false }
    }
    BackHandler { leave() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = ::leave, enabled = !busy) { Text("뒤로") }
                TextButton(onClick = { if (edited) reloadDialog = true else { initialized = false; refresh++ } }, enabled = !busy) { Text("다시 불러오기") }
            }
            Text(if (album.kind == "WEEKLY") "이번 주 우리 가족" else "함께 찍은 하루", style = MaterialTheme.typography.headlineMedium)
            Text("${album.date} ~ ${album.endDate}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("같은 기간의 사진을 모았어요. 함께한 순간을 직접 골라 완성해 보세요.")
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item { ErrorBlock("앨범을 확인해 주세요", message, onRetry = { if (edited) reloadDialog = true else { initialized = false; refresh++ } }) } }
        if (detail != null && initialized) {
            item {
                Text("후보 ${detail!!.summary.photoCount}장 · " + detail!!.devices.joinToString(" · ") { "${it.name} ${it.count}장" }, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it; edited = true; savedMessage = false }, label = { Text("앨범 제목") }, supportingText = { Text("${title.trim().length}/100") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it; edited = true; savedMessage = false }, label = { Text("함께 남기는 기록") }, supportingText = { Text("${note.length}/1000") }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
                Text("고른 사진 ${selected.size}/100장", style = MaterialTheme.typography.titleLarge)
                Text("첫 사진이 표지가 돼요. 사진을 누르면 크게 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
            }
            itemsIndexed(selected, key = { _, photo -> "selected-${photo.id}" }) { index, photo ->
                Card {
                    Row(Modifier.padding(8.dp)) {
                        ThumbCell(photo, cfg.serverUrl, cfg.apiKey, { preview = index }, Modifier.width(88.dp))
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("${index + 1}. ${photo.takenAt?.take(10).orEmpty()}")
                            Row {
                                TextButton(enabled = !busy && index > 0, onClick = { selected = selected.toMutableList().apply { add(index - 1, removeAt(index)) }; edited = true; savedMessage = false }) { Text("위로") }
                                TextButton(enabled = !busy && index < selected.lastIndex, onClick = { selected = selected.toMutableList().apply { add(index + 1, removeAt(index)) }; edited = true; savedMessage = false }) { Text("아래로") }
                            }
                            TextButton(enabled = !busy, onClick = { selected = selected.filterNot { it.id == photo.id }; edited = true; savedMessage = false }) { Text("선택 제외") }
                        }
                    }
                }
            }
            item {
                if (selected.isEmpty()) Text("아래 후보에서 사진을 골라 주세요.")
                Button(enabled = !busy && title.trim().length in 1..100 && note.length <= 1000 && selected.size in 1..100,
                    modifier = Modifier.fillMaxWidth(), onClick = {
                        busy = true; error = null
                        scope.launch {
                            try {
                                accept(api.saveFamilyAlbum(album.kind, album.date, SaveFamilyAlbum(title, note, selected.map { it.id }, revision)))
                                savedMessage = true
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = if (e is retrofit2.HttpException && e.code() == 409) "다른 곳에서 앨범이 바뀌었어요. 현재 편집 내용은 남아 있어요. 다시 불러온 뒤 확인해 주세요." else e.toFriendlyMessage() }
                            finally { busy = false }
                        }
                    }) { Text(if (busy) "처리 중…" else "앨범 저장") }
                if (savedMessage) Text("저장했어요. 새 사진이 올라와도 고른 사진과 순서는 유지돼요.", color = MaterialTheme.colorScheme.primary)
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("이 기간의 사진 후보", style = MaterialTheme.typography.titleLarge)
                Text("사진을 눌러 선택하거나 제외할 수 있어요.")
            }
            candidates.chunked(3).forEach { row -> item(key = "candidate-${row.first().id}") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { photo ->
                        val checked = selected.any { it.id == photo.id }
                        Column(Modifier.weight(1f)) {
                            ThumbCell(photo, cfg.serverUrl, cfg.apiKey, {
                                if (!busy && (checked || selected.size < 100)) {
                                    selected = if (checked) selected.filterNot { it.id == photo.id } else selected + photo
                                    edited = true; savedMessage = false
                                }
                            }, Modifier.fillMaxWidth())
                            Text(if (checked) "✓ 선택됨" else "선택하기", color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            } }
            candidateError?.let { item { ErrorBlock("후보를 불러올 수 없어요", it, onRetry = ::loadCandidates) } }
            item {
                if (!candidateLoaded || cursor != null) OutlinedButton(onClick = ::loadCandidates, enabled = !candidateBusy && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (candidateBusy) "불러오는 중…" else "후보 사진 더 보기") }
                else Text(if (candidates.isEmpty()) "이 기간에 백업된 사진이 아직 없어요." else "모든 후보를 확인했어요.")
            }
        }
    }
    if (exitDialog || reloadDialog) AlertDialog(onDismissRequest = { exitDialog = false; reloadDialog = false },
        title = { Text("저장하지 않은 변경이 있어요") }, text = { Text("계속하면 현재 편집한 내용이 사라져요.") },
        confirmButton = { TextButton(onClick = {
            if (exitDialog) onBack() else { initialized = false; edited = false; refresh++; candidates = emptyList(); cursor = null; candidateLoaded = false }
            exitDialog = false; reloadDialog = false
        }) { Text("변경 버리기") } }, dismissButton = { TextButton(onClick = { exitDialog = false; reloadDialog = false }) { Text("계속 편집") } })
    preview?.let { FullScreenViewer(selected, it, cfg.serverUrl, cfg.apiKey, onClose = { preview = null }) }
}
