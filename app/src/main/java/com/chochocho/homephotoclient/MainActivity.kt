package com.chochocho.homephotoclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.chochocho.homephotoclient.backup.BackupEngine
import com.chochocho.homephotoclient.data.SettingsRepository
import com.chochocho.homephotoclient.ui.*
import com.chochocho.homephotoclient.ui.theme.HomePhotoSpacing
import com.chochocho.homephotoclient.ui.theme.HomePhotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val repository = SettingsRepository(applicationContext)
        val backupEngine = BackupEngine.get(applicationContext)
        setContent {
            HomePhotoTheme {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                val screenStates = rememberSaveableStateHolder()
                val tabs = listOf("홈", "사진", "검색", "인물", "백업")
                val icons = listOf(R.drawable.ic_home, R.drawable.ic_photos, R.drawable.ic_search, R.drawable.ic_people, R.drawable.ic_backup)
                BackHandler(enabled = settingsOpen || selectedTab != 0) {
                    if (settingsOpen) settingsOpen = false else selectedTab = 0
                }
                Scaffold(
                    topBar = {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = HomePhotoSpacing.screen),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (settingsOpen) TextButton(onClick = { settingsOpen = false }) { Text("뒤로") }
                            Text("HomePhoto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (!settingsOpen) IconButton(onClick = { settingsOpen = true }) {
                                Icon(painterResource(R.drawable.ic_settings), contentDescription = "설정")
                            }
                        }
                    },
                    bottomBar = {
                        if (!settingsOpen) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            tabs.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = { Icon(painterResource(icons[index]), contentDescription = null) },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        screenStates.SaveableStateProvider(if (settingsOpen) "settings" else "tab-$selectedTab") {
                            if (settingsOpen) SettingsScreen(repository) else when (selectedTab) {
                                0 -> HomeScreen(repository, onPhotos = { selectedTab = 1 }, onPeople = { selectedTab = 3 }, onBackup = { selectedTab = 4 })
                                1 -> TimelineScreen(repository)
                                2 -> SearchScreen(repository, onPeople = { selectedTab = 3 })
                                3 -> PeopleScreen(repository)
                                else -> BackupScreen(backupEngine)
                            }
                        }
                    }
                }
            }
        }
    }
}
