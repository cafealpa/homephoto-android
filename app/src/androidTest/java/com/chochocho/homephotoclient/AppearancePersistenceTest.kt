package com.chochocho.homephotoclient

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chochocho.homephotoclient.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearancePersistenceTest {
    @Test fun themeAndModePersistIndependentlyWithoutChangingConnectionSettings() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SettingsRepository(context)
        val original = repository.appearance.first()
        val connection = repository.settings.first()
        try {
            for (palette in AppPalette.entries) {
                for (mode in AppColorMode.entries) {
                    val colorWrite = async { repository.setPalette(palette) }
                    val modeWrite = async { repository.setColorMode(mode) }
                    colorWrite.await(); modeWrite.await()
                    val recreated = SettingsRepository(context)
                    assertEquals(AppearanceSettings(palette, mode), recreated.appearance.first())
                    assertEquals(connection, recreated.settings.first())
                    // 기존 설정을 저장해도 색상 선택이 지워지지 않아야 한다.
                    recreated.setAutoBackup(connection.autoBackupEnabled)
                    assertEquals(AppearanceSettings(palette, mode), recreated.appearance.first())
                }
            }
        } finally {
            repository.setPalette(original.palette)
            repository.setColorMode(original.mode)
        }
    }
}
