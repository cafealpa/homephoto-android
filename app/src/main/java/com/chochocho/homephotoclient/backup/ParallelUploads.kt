package com.chochocho.homephotoclient.backup

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** 파일 수와 무관하게 고정된 수의 작업자만 실행한다. 취소 시 진행 중인 작업도 함께 취소된다. */
internal suspend fun <T> parallelUploads(
    items: List<T>,
    concurrency: Int = 3,
    upload: suspend (T) -> Unit,
) = coroutineScope {
    require(concurrency > 0)
    val next = AtomicInteger()
    repeat(minOf(concurrency, items.size)) {
        launch {
            while (true) {
                ensureActive()
                val index = next.getAndIncrement()
                if (index >= items.size) break
                upload(items[index])
            }
        }
    }
}
