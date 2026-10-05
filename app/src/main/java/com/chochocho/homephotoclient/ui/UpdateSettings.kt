package com.chochocho.homephotoclient.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chochocho.homephotoclient.update.AppUpdater

@Composable
internal fun UpdateSettings() {
    val context = LocalContext.current
    val updater = remember { AppUpdater.get(context) }
    val state by updater.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("앱 업데이트", style = MaterialTheme.typography.titleMedium)
        Text("현재 ${updater.installedName} (${updater.installedVersion}) · GitHub 정식 릴리즈", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = updater::check, enabled = !state.busy) { Text("새 버전 확인") }
        state.release?.let { release ->
            Text("${release.versionName} (${release.versionCode}) · ${release.sizeBytes / 1024 / 1024} MB", style = MaterialTheme.typography.titleSmall)
            if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { if (state.ready) updater.install() else updater.download() }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()) { Text(if (state.ready) "설치 / 다시 설치" else "APK 다운로드") }
        }
        if (state.busy) {
            state.percent?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()); Text("$it%") }
                ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(state.message, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
