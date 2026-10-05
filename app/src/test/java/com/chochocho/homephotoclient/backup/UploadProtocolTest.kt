package com.chochocho.homephotoclient.backup

import com.chochocho.homephotoclient.data.CheckResponse
import com.chochocho.homephotoclient.data.UploadReceipt
import com.chochocho.homephotoclient.data.storedHashes
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class UploadProtocolTest {
    private val gson = Gson()

    @Test fun `queued receipt is not mistaken for stored asset or missing upload`() {
        val response = gson.fromJson("""{"missing":["m"],"deleted":["d"],"queued":["q"]}""", CheckResponse::class.java)
        assertEquals(listOf("s"), response.storedHashes(listOf("m", "d", "q", "s")))
        assertEquals(listOf("q"), response.queued)
        val completed = gson.fromJson("""{"missing":[],"deleted":[],"queued":[]}""", CheckResponse::class.java)
        assertEquals(listOf("q"), completed.storedHashes(listOf("q")))
    }

    @Test fun `legacy check response remains compatible`() {
        val response = gson.fromJson("""{"missing":["m"]}""", CheckResponse::class.java)
        assertEquals(listOf("s"), response.storedHashes(listOf("m", "s")))
        assertNull(response.queued)
    }

    @Test fun `upload response decodes both queued receipt and legacy asset`() {
        val receipt = gson.fromJson("""{"hash":"q","status":"QUEUED"}""", UploadReceipt::class.java)
        assertEquals("QUEUED", receipt.status)
        val stored = gson.fromJson("""{"id":10,"hash":"s","mediaType":"PHOTO"}""", UploadReceipt::class.java)
        assertEquals("s", stored.hash)
        assertNull(stored.status)
    }
}
