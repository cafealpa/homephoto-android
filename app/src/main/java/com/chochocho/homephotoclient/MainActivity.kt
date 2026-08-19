package com.chochocho.homephotoclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chochocho.homephotoclient.backup.BackupEngine
import com.chochocho.homephotoclient.data.SettingsRepository
import com.chochocho.homephotoclient.ui.BackupScreen
import com.chochocho.homephotoclient.ui.PeopleScreen
import com.chochocho.homephotoclient.ui.SettingsScreen
import com.chochocho.homephotoclient.ui.TimelineScreen
import com.chochocho.homephotoclient.ui.components.HomePhotoBottomNav
import com.chochocho.homephotoclient.ui.theme.HomePhotoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35+ 강제 edge-to-edge에서 상태바 아이콘 색 지정.
        // 앱이 다크 전용이므로 항상 밝은 아이콘이 필요하다.
        enableEdgeToEdge()
        val settingsRepository = SettingsRepository(applicationContext)
        val backupEngine = BackupEngine.get(applicationContext)

        setContent {
            HomePhotoTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf("사진", "인물", "백업", "설정")

                // 1c 확장: 상단 TabRow 대신 하단 라벨 내비게이션.
                Scaffold(
                    bottomBar = {
                        HomePhotoBottomNav(
                            tabs = tabs,
                            selectedIndex = selectedTab,
                            onSelect = { selectedTab = it },
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> TimelineScreen(repository = settingsRepository)
                            1 -> PeopleScreen(repository = settingsRepository)
                            2 -> BackupScreen(engine = backupEngine)
                            else -> SettingsScreen(repository = settingsRepository)
                        }
                    }
                }
            }
        }
    }
}
