package com.chochocho.homephotoclient.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.chochocho.homephotoclient.data.AppColorMode
import com.chochocho.homephotoclient.data.AppPalette
import com.chochocho.homephotoclient.data.SettingsRepository
import com.chochocho.homephotoclient.ui.theme.HomePhotoSpacing
import com.chochocho.homephotoclient.ui.theme.homePhotoColorScheme
import java.io.IOException
import kotlinx.coroutines.launch

@Composable
internal fun AppearanceSettingsSection(repository: SettingsRepository) {
    val appearance by repository.appearance.collectAsState(initial = null)
    val current = appearance ?: return
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    fun save(action: suspend () -> Unit) {
        saving = true
        error = null
        scope.launch {
            try { action() }
            catch (_: IOException) { error = "테마를 저장하지 못했어요. 다시 선택해 주세요." }
            finally { saving = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.section)) {
        Text("화면 디자인", style = MaterialTheme.typography.headlineSmall)
        Text("색상 테마", style = MaterialTheme.typography.titleMedium)
        Text("선택하면 바로 적용되고 자동으로 저장돼요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item)) {
            AppPalette.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item)) {
                    pair.forEach { palette ->
                        val selected = current.palette == palette
                        val colors = homePhotoColorScheme(palette, current.mode.isDark(isSystemInDarkTheme()))
                        Surface(
                            modifier = Modifier.weight(1f).selectable(
                                selected = selected, enabled = !saving, role = Role.RadioButton,
                                onClick = { save { repository.setPalette(palette) } },
                            ),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(HomePhotoSpacing.hairline,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(HomePhotoSpacing.section), verticalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(palette.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    if (selected) Text("✓", style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.tight)) {
                                    listOf(colors.background, colors.surfaceContainer, colors.primary).forEach { color ->
                                        Surface(Modifier.size(HomePhotoSpacing.swatch), color = color, shape = CircleShape,
                                            border = BorderStroke(HomePhotoSpacing.hairline, colors.outline)) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text("화면 모드", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            AppColorMode.entries.forEach { mode ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = HomePhotoSpacing.touchTarget)
                        .selectable(selected = current.mode == mode, enabled = !saving, role = Role.RadioButton,
                            onClick = { save { repository.setColorMode(mode) } }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HomePhotoSpacing.item),
                ) {
                    RadioButton(selected = current.mode == mode, onClick = null, enabled = !saving)
                    Text(mode.title)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
