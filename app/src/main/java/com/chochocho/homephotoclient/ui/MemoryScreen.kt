package com.chochocho.homephotoclient.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chochocho.homephotoclient.data.*
import com.chochocho.homephotoclient.ui.components.ErrorBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MemoryScreen(api: HomePhotoApi, cfg: AppSettings, id: Long, album: Boolean, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<MemoryDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var showSaved by rememberSaveable { mutableStateOf(album) }
    var savedAlbumId by rememberSaveable { mutableStateOf<Long?>(if (album) id else null) }
    val scope = rememberCoroutineScope()
    BackHandler { if (!saving) { if (editor) editor = false else onBack() } }
    // Retrofit proxies do not provide stable structural equality for Compose effect keys.
    LaunchedEffect(cfg.serverUrl, cfg.internalServerUrl, cfg.apiKey, id, refresh, showSaved) {
        loading = true; error = null
        try {
            detail = if (showSaved) api.memoryAlbum(savedAlbumId ?: id) else api.memory(id)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.toFriendlyMessage() }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, enabled = !saving) { Text("뒤로") }
            Column(Modifier.weight(1f).padding(8.dp)) {
                Text(detail?.summary?.title ?: "추억 불러오기", style = MaterialTheme.typography.titleMedium)
                detail?.let { Text("${memoryPeriod(it.summary)} · ${it.photos.size}장", style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { message -> ErrorBlock("사진을 불러올 수 없어요", message, onRetry = { refresh++ }) }
        val value = detail
        if (value != null && value.photos.isNotEmpty()) {
            Box(Modifier.weight(1f)) {
                FullScreenViewer(value.photos, 0, cfg.serverUrl, cfg.apiKey, onClose = onBack)
            }
            Text("좌우로 넘겨 그날의 사진을 감상해 보세요.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            if (saved) Text("앨범으로 간직했어요.", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
            if (!showSaved) Button(onClick = {
                if (value.summary.albumId != null) {
                    savedAlbumId = value.summary.albumId; showSaved = true
                } else {
                    title = "${value.summary.title} · ${value.summary.startDate}"
                    chosen = value.photos.map { it.id }; editor = true
                }
            }, modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = !loading) {
                Text(if (value.summary.albumId == null) "앨범으로 간직하기" else "저장한 앨범 보기")
            }
        } else if (!loading && error == null) {
            Text("이 묶음에서 볼 수 있는 사진이 없어요.", Modifier.padding(16.dp))
        }
    }
    if (editor && detail != null) {
        val value = detail!!
        AlertDialog(onDismissRequest = { if (!saving) editor = false },
            title = { Text("앨범으로 간직하기") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(title, { title = it }, label = { Text("앨범 제목") }, singleLine = true, enabled = !saving)
                        Text("${chosen.size}장 선택 · 첫 사진이 표지가 돼요.")
                        Text("체크를 해제해 제외하거나 순서를 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    val ordered = chosen.mapNotNull { selected -> value.photos.find { it.id == selected } } + value.photos.filterNot { it.id in chosen }
                    itemsIndexed(ordered, key = { _, photo -> photo.id }) { _, photo ->
                        val index = chosen.indexOf(photo.id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = index >= 0, enabled = !saving, onCheckedChange = { checked ->
                                chosen = if (checked) chosen + photo.id else chosen - photo.id
                            })
                            ThumbCell(photo, cfg.serverUrl, cfg.apiKey, {}, Modifier.width(56.dp))
                            Column(Modifier.weight(1f)) {
                                Text(photo.takenAt?.take(10).orEmpty(), style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(enabled = !saving && index > 0, onClick = {
                                        chosen = chosen.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    }) { Text("위로") }
                                    TextButton(enabled = !saving && index >= 0 && index < chosen.lastIndex, onClick = {
                                        chosen = chosen.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    }) { Text("아래로") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = !saving && title.trim().length in 1..100 && chosen.isNotEmpty(), onClick = {
                saving = true; error = null
                scope.launch {
                    try {
                        val result = api.saveMemoryAlbum(id, SaveMemoryAlbum(title, chosen))
                        detail = result; savedAlbumId = result.summary.albumId; saved = true; editor = false; showSaved = true
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.toFriendlyMessage() }
                    finally { saving = false }
                }
            }) { Text(if (saving) "저장 중…" else "앨범 저장") } },
            dismissButton = { TextButton(enabled = !saving, onClick = { editor = false; error = null }) { Text("취소") } })
    }
}

internal fun memoryPeriod(memory: MemorySummary): String = if (memory.startDate == memory.endDate) memory.startDate else "${memory.startDate} ~ ${memory.endDate}"
