package com.chochocho.homephotoclient.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.chochocho.homephotoclient.backup.BackupEngine
import com.chochocho.homephotoclient.backup.BackupState
import com.chochocho.homephotoclient.backup.formatElapsed
import com.chochocho.homephotoclient.data.local.BackupDb
import com.chochocho.homephotoclient.ui.components.HomePhotoCard
import com.chochocho.homephotoclient.ui.components.PrimaryActionButton
import com.chochocho.homephotoclient.ui.components.SecondaryActionButton
import com.chochocho.homephotoclient.data.local.FailureEntry
import com.chochocho.homephotoclient.data.local.LocalAsset
import kotlinx.coroutines.delay

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
    }

@Composable
fun BackupScreen(engine: BackupEngine) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            requiredPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.all { it } }

    val state by engine.state.collectAsState()
    val counts by engine.counts.collectAsState()
    val failures by engine.failures.collectAsState()
    var showSkipped by remember { mutableStateOf(false) }
    var showFailures by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { engine.refreshCounts() }

    if (showSkipped) {
        SkippedManager(engine = engine, onClose = {
            showSkipped = false
            engine.refreshCounts()
        })
        return
    }
    if (showFailures) {
        FailureLogScreen(
            entries = failures,
            onClear = { engine.clearFailureLog() },
            onClose = { showFailures = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("백업", style = MaterialTheme.typography.headlineSmall)

        if (!granted) {
            SummaryRow("사진·동영상을 읽으려면 권한이 필요합니다.")
            PrimaryActionButton("권한 허용", onClick = { launcher.launch(requiredPermissions()) })
            return@Column
        }

        HomePhotoCard(verticalGap = 4.dp) {
            val total = counts.values.sum()
            SummaryRow("발견된 파일: ${total}개")
            SummaryRow("백업 완료: ${counts["UPLOADED"] ?: 0}개")
            if ((counts["SERVER_QUEUED"] ?: 0) > 0) {
                SummaryRow("서버 수신 완료 · 원본 저장 대기: ${counts["SERVER_QUEUED"]}개")
                SummaryRow("서버가 자동으로 저장합니다. 다음 백업 때 상태를 확인하며 재전송하지 않습니다.")
            }
            SummaryRow("대기 중: ${(counts["NEW"] ?: 0) + (counts["HASHED"] ?: 0)}개")
            SummaryRow(
                "실패: ${counts["FAILED"] ?: 0}개",
                color = if ((counts["FAILED"] ?: 0) > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            val skipped = counts["SKIPPED"] ?: 0
            if (skipped > 0) {
                SummaryRow(
                    "스킵(서버에서 삭제됨): ${skipped}개",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if ((counts["SKIPPED"] ?: 0) > 0) {
            SecondaryActionButton(
                "스킵된 사진 관리 (${counts["SKIPPED"]})",
                onClick = { showSkipped = true },
            )
        }

        when (val s = state) {
            is BackupState.Working -> {
                // 1초마다 갱신되는 경과 시간
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(1000)
                    }
                }
                if (s.total > 0) {
                    LinearProgressIndicator(
                        progress = { s.done.toFloat() / s.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${s.phase} ${s.done}/${s.total} — ${s.current ?: ""}")
                    Text(
                        listOfNotNull(
                            "경과 ${formatElapsed(now - s.startedAtMillis)}",
                            s.speedBps?.let { com.chochocho.homephotoclient.backup.formatSpeed(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("${s.phase} 중...")
                    Text(
                        "경과 ${formatElapsed(now - s.startedAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SecondaryActionButton("중지", onClick = { engine.cancel() })
            }
            is BackupState.Done -> {
                Text(
                    "전송 종료 — 저장 완료 ${s.uploaded}개, 서버에 이미 있음 ${s.alreadyOnServer}개, 원본 저장 대기 ${s.serverQueued}개, 실패 ${s.failed}개" +
                        " (소요 시간 ${formatElapsed(s.elapsedMillis)})",
                    color = MaterialTheme.colorScheme.primary,
                )
                PrimaryActionButton("다시 백업", onClick = { engine.start() })
            }
            is BackupState.WaitingForStorage -> {
                Text(s.message, color = MaterialTheme.colorScheme.primary)
                Text("기기 사진은 유지됩니다. Wi-Fi 연결과 배터리 여유가 있을 때 공간을 다시 확인하고 자동 재개합니다.")
                PrimaryActionButton("지금 다시 확인", onClick = { engine.start() })
                SecondaryActionButton("자동 재개 취소", onClick = { engine.cancel() })
            }
            is BackupState.Error -> {
                Text("오류: ${s.message}", color = MaterialTheme.colorScheme.error)
                PrimaryActionButton("다시 시도", onClick = { engine.start() })
            }
            BackupState.Idle -> {
                PrimaryActionButton("지금 백업", onClick = { engine.start() })
            }
        }

        // 실패 이력 — 최근 몇 건은 탭에서 바로 보이고, 전체는 별도 화면에서
        if (failures.isNotEmpty()) {
            HomePhotoCard(verticalGap = 8.dp) {
                Text(
                    "실패 이력",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                failures.take(RECENT_FAILURES_INLINE).forEach { FailureRow(it) }
                if (failures.size > RECENT_FAILURES_INLINE) {
                    TextButton(onClick = { showFailures = true }) {
                        Text("전체 보기 (${failures.size}건)")
                    }
                } else {
                    TextButton(onClick = { showFailures = true }) { Text("자세히 / 지우기") }
                }
            }
        }
    }
}

private const val RECENT_FAILURES_INLINE = 3

/** 요약 카드 한 줄 — bodyMedium(14/20, 자간 .25). 색만 상황에 따라 바꾼다. */
@Composable
private fun SummaryRow(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

private fun formatFailureTime(epochMillis: Long): String =
    java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMillis))

@Composable
private fun FailureRow(entry: FailureEntry) {
    Column {
        Text(
            "${formatFailureTime(entry.at)} · [${entry.stage}] ${entry.displayName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            entry.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** 실패 이력 전체 목록. 파일별로 언제·어느 단계에서·왜 실패했는지 보여준다. */
@Composable
private fun FailureLogScreen(entries: List<FailureEntry>, onClear: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← 뒤로") }
            Text(
                "실패 이력 (${entries.size}건)",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear, enabled = entries.isNotEmpty()) { Text("지우기") }
        }
        Text(
            "실패한 항목은 다음 백업 때 자동으로 다시 시도됩니다. 최근 ${BackupDb.MAX_FAILURE_LOG}건까지 보관합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entries.isEmpty()) {
            Text("실패 이력이 없습니다.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(entries.size, key = { entries[it].id }) { i -> FailureRow(entries[i]) }
            }
        }
    }
}

/** 서버에서 삭제되어 백업이 스킵된 사진을 모아 보고, 선택해서 다시 올리는 화면. */
@Composable
private fun SkippedManager(engine: BackupEngine, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var items by remember { mutableStateOf<List<LocalAsset>?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { items = engine.skippedItems() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← 뒤로") }
            Text(
                "스킵된 사진",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "서버에서 삭제되어 백업에서 제외된 사진입니다. 선택 후 다시 올릴 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val list = items
        when {
            list == null -> CircularProgressIndicator()
            list.isEmpty() -> Text("스킵된 사진이 없습니다.")
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(list.size, key = { list[it].uri }) { i ->
                        val item = list[i]
                        val isSelected = item.uri in selected
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable {
                                    if (isSelected) selected.remove(item.uri) else selected.add(item.uri)
                                }
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary)
                                    else Modifier
                                ),
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isSelected) {
                                Text(
                                    "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp),
                                )
                            }
                        }
                    }
                }
                PrimaryActionButton(
                    label = "선택 ${selected.size}개 다시 올리기",
                    enabled = selected.isNotEmpty(),
                    onClick = {
                        engine.requeueAndBackup(selected.toList())
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
