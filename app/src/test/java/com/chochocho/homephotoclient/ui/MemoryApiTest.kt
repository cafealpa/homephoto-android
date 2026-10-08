package com.chochocho.homephotoclient.ui

import com.chochocho.homephotoclient.data.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MemoryApiTest {
    private val requests = mutableListOf<Request>()
    private fun api(reply: (Request) -> Pair<Int, String>): HomePhotoApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            val (code, body) = reply(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return Retrofit.Builder().baseUrl("https://homephoto.test/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(HomePhotoApi::class.java)
    }
    private val summary = """{"id":4,"title":"다시 꺼내 보는 하루","startDate":"2022-05-14","endDate":"2022-05-14","photoCount":2,"albumId":7,"coverAssetId":9}"""
    private val detail = """{"summary":$summary,"photos":[{"id":9,"hash":"a"},{"id":2,"hash":"b"}]}"""

    @Test fun `daily response and saved album preserve separate identities and selected order`() = runBlocking {
        val service = api { request -> 200 to if (request.url.encodedPath.endsWith("/home"))
            """{"date":"2026-10-08","memories":[$summary],"saved":[$summary]}""" else detail }
        val home = service.memoriesHome()
        assertEquals("2026-10-08", home.date)
        assertEquals(4L, home.memories.single().id)
        assertEquals(7L, home.saved.single().albumId)
        assertEquals(listOf(9L, 2L), service.memory(4).photos.map { it.id })
        assertEquals(listOf(9L, 2L), service.memoryAlbum(7).photos.map { it.id })
        assertEquals(listOf("/api/v1/memories/home", "/api/v1/memories/4", "/api/v1/memories/albums/7"), requests.map { it.url.encodedPath })
    }
    @Test fun `album save posts edited title and order atomically`() = runBlocking {
        var posted = ""
        val service = api { request ->
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer); posted = buffer.readUtf8()
            200 to detail
        }
        val saved = service.saveMemoryAlbum(4, SaveMemoryAlbum("우리의 하루", listOf(9, 2)))
        val body = JsonParser.parseString(posted).asJsonObject
        assertEquals("우리의 하루", body["title"].asString)
        assertEquals(listOf(9L, 2L), body["assetIds"].asJsonArray.map { it.asLong })
        assertEquals("POST", requests.single().method)
        assertEquals("/api/v1/memories/4/album", requests.single().url.encodedPath)
        assertEquals(7L, saved.summary.albumId)
    }
    @Test fun `unsupported and failed responses remain distinct from empty memories`() = runBlocking {
        for (status in listOf(404, 503)) {
            try { api { status to "{}" }.memoriesHome(); fail("HTTP error expected") }
            catch (e: HttpException) { assertEquals(status, e.code()) }
        }
        assertTrue(api { 200 to """{"date":"2026-10-08","memories":[],"saved":[]}""" }.memoriesHome().memories.isEmpty())
    }
}
