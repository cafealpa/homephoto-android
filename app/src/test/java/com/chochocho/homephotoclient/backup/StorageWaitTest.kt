package com.chochocho.homephotoclient.backup

import com.chochocho.homephotoclient.data.BackupCapacity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class StorageWaitTest {
    @Test fun `capacity rejection is a distinct waiting outcome and legacy 404 is compatible`() {
        checkUploadCapacity(Response.success(BackupCapacity(true, null)))
        checkUploadCapacity(Response.error(404, "".toResponseBody()))
        for (response in listOf(Response.success(BackupCapacity(false, "공간 부족")), Response.error(507, "".toResponseBody()))) {
            try { checkUploadCapacity(response); fail("must wait") } catch (_: ServerStorageWait) { }
        }
        try { checkUploadCapacity(Response.error(401, "".toResponseBody())); fail("must report auth") }
        catch (e: retrofit2.HttpException) { assertEquals(401, e.code()) }
    }

    @Test fun `storage pressure stops remaining parallel uploads without marking them completed`() = runBlocking {
        val started = AtomicInteger(); val completed = AtomicInteger()
        try {
            parallelUploads((1..100).toList()) { item ->
                started.incrementAndGet()
                if (item == 1) { delay(20); throw ServerStorageWait("full") }
                delay(2000)
                completed.incrementAndGet()
            }
            fail("must wait")
        } catch (_: ServerStorageWait) { }
        assertTrue(started.get() <= 3)
        assertEquals(0, completed.get())
    }
}
