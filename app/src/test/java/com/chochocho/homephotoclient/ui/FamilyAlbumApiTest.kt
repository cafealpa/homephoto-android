package com.chochocho.homephotoclient.ui

import com.chochocho.homephotoclient.data.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FamilyAlbumApiTest {
    @Test fun `save carries ordered selection and revision while candidate cursor round trips`() = runBlocking {
        val requests = mutableListOf<Request>()
        var posted = ""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            val body = if (request.method == "POST") {
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer); posted = buffer.readUtf8()
                """{"summary":{"kind":"WEEKLY","date":"2026-10-05","endDate":"2026-10-11","title":"우리 가족","albumId":3,"photoCount":2,"deviceCount":2},"note":"산책","revision":5,"selected":[{"id":9,"hash":"a"},{"id":2,"hash":"b"}],"devices":[]}"""
            } else """{"items":[],"nextCursor":null}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("ok")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://homephoto.test/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(HomePhotoApi::class.java)
        val result = api.saveFamilyAlbum("WEEKLY", "2026-10-05", SaveFamilyAlbum("우리 가족", "산책", listOf(9, 2), 4))
        val json = JsonParser.parseString(posted).asJsonObject
        assertEquals(4, json["revision"].asInt)
        assertEquals(listOf(9L, 2L), json["assetIds"].asJsonArray.map { it.asLong })
        assertEquals("우리 가족", json["title"].asString)
        assertEquals(5, result.revision); assertEquals(listOf(9L, 2L), result.selected.map { it.id })
        assertEquals("/api/v1/family-albums/WEEKLY/2026-10-05", requests.first().url.encodedPath)
        api.familyAlbumCandidates("TOGETHER", "2026-10-05", "2026-10-05T12:00:00~9")
        assertEquals("2026-10-05T12:00:00~9", requests.last().url.queryParameter("cursor"))
    }
}
