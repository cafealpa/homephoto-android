package com.chochocho.homephotoclient.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chochocho.homephotoclient.data.HomePhotoApi
import com.chochocho.homephotoclient.data.toFriendlyMessage
import kotlinx.coroutines.CancellationException

/** 사진 페이지와 별도로 전체 월 목록을 조회한다. */
internal class TimelineMonthsState(private val api: HomePhotoApi) {
    var months by mutableStateOf<List<String>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load() {
        if (loading) return
        loading = true
        error = null
        try {
            months = api.months().map { it.yearMonth }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.toFriendlyMessage()
        } finally {
            loading = false
        }
    }
}
