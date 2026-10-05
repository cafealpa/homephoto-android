package com.chochocho.homephotoclient.ui

import com.chochocho.homephotoclient.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PhotoDiscoveryTest {
    private val requests = mutableListOf<Request>()
    private fun api(reply: (Request) -> Pair<Int, String>): HomePhotoApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request)
            val (code, body) = reply(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return Retrofit.Builder().baseUrl("https://homephoto.test/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(HomePhotoApi::class.java)
    }

    @Test fun `semantic request sends Korean query and inclusive leap month dates preserving relevance`() = runBlocking {
        val service = api { 200 to """{"items":[{"id":9,"hash":"a"},{"id":2,"hash":"b"}],"indexed_photos":100,"approximate":true}""" }
        val result = service.discover(PhotoQuery("  바닷가 가족사진  ", "2024-02"))
        val url = requests.single().url
        assertEquals("/api/v1/photos/search", url.encodedPath)
        assertEquals("바닷가 가족사진", url.queryParameter("query"))
        assertEquals("2024-02-01", url.queryParameter("start_date"))
        assertEquals("2024-02-29", url.queryParameter("end_date"))
        assertEquals("24", url.queryParameter("limit"))
        assertEquals(listOf(9L, 2L), result.items.map { it.id })
        assertNull(result.nextCursor)
        assertEquals(100L, result.indexedPhotos)
    }

    @Test fun `all time semantic request omits date parameters`() = runBlocking {
        val service = api { 200 to """{"items":[],"indexed_photos":0,"approximate":true}""" }
        service.discover(PhotoQuery("여행"))
        assertNull(requests.single().url.queryParameter("start_date"))
        assertNull(requests.single().url.queryParameter("end_date"))
    }

    @Test fun `blank keyword browses assets with month and encoded cursor`() = runBlocking {
        val service = api { 200 to """{"items":[],"nextCursor":"next"}""" }
        val result = service.discover(PhotoQuery("   ", "2026-10"), "2026-10-01T12:00:00|42")
        val url = requests.single().url
        assertEquals("/api/v1/assets", url.encodedPath)
        assertEquals("2026-10", url.queryParameter("yearMonth"))
        assertEquals("2026-10-01T12:00:00|42", url.queryParameter("cursor"))
        assertEquals("next", result.nextCursor)
        assertNull(result.indexedPhotos)
    }

    @Test fun `overlong input never makes a request`() = runBlocking {
        val service = api { error("must not call") }
        assertNull(PhotoQuery("가".repeat(500)).validationMessage)
        try {
            service.discover(PhotoQuery("가".repeat(501)))
            fail("validation expected")
        } catch (_: IllegalArgumentException) { assertTrue(requests.isEmpty()) }
    }

    @Test fun `service unavailable stays retryable and retry clears the message`() = runBlocking {
        var unavailable = true
        val service = api {
            if (unavailable) 503 to "{}" else 200 to """{"items":[{"id":3,"hash":"a"}],"indexed_photos":1,"approximate":true}"""
        }
        val state = DiscoveryState(service, PhotoQuery("산책"))
        state.loadMore()
        assertTrue(state.error!!.contains("검색 서비스"))
        assertFalse(state.loading)
        assertTrue(state.canLoadMore)
        unavailable = false
        state.loadMore()
        assertNull(state.error)
        assertEquals(listOf(3L), state.items.map { it.id })
        assertFalse(state.canLoadMore)
        state.loadMore()
        assertEquals(2, requests.size)
    }

    @Test fun `asset pages deduplicate overlaps and stop at last cursor`() = runBlocking {
        val service = api { request ->
            200 to if (request.url.queryParameter("cursor") == null)
                """{"items":[{"id":3,"hash":"a"}],"nextCursor":"next"}"""
            else """{"items":[{"id":3,"hash":"a"},{"id":2,"hash":"b"}],"nextCursor":null}"""
        }
        val state = DiscoveryState(service, PhotoQuery())
        state.loadMore()
        assertTrue(state.canLoadMore)
        state.loadMore()
        assertEquals(listOf(3L, 2L), state.items.map { it.id })
        assertFalse(state.canLoadMore)
        state.loadMore()
        assertEquals(2, requests.size)
    }

    @Test fun `old server search endpoint explains alternative browsing`() = runBlocking {
        val state = DiscoveryState(api { 404 to "{}" }, PhotoQuery("가족"))
        state.loadMore()
        assertTrue(state.error!!.contains("날짜별·인물별"))
    }
}
