package com.chochocho.homephotoclient.data

import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class ServerRoutingTest {
    private val cfg = AppSettings("https://external.test/public", "key", false, "phone", "http://internal.test/local")
    private var route = NetworkRoute("wifi1", true)
    private var time = 0L
    private val seen = mutableListOf<Request>()
    private fun client(settings: AppSettings = cfg, reply: (Request) -> Int): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(ServerRoutingInterceptor({ settings }, { route }, { time }))
            .addInterceptor { chain ->
                val request = chain.request()
                seen += request
                val code = reply(request)
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
                    .message("test").body("ok".toResponseBody()).build()
            }.build()
    private fun call(client: OkHttpClient, method: String = "GET"): Response = client.newCall(
        Request.Builder().url("https://external.test/public/api/v1/assets?cursor=a%2Fb")
            .header("X-Api-Key", "key").method(method, if (method == "POST") "data".toRequestBody() else null).build()
    ).execute()

    @Test fun `wifi uses internal while cellular uses external and preserves path query headers`() {
        val client = client { 200 }
        call(client).close()
        assertEquals("http://internal.test/local/api/v1/assets?cursor=a%2Fb", seen.last().url.toString())
        assertEquals("key", seen.last().header("X-Api-Key"))
        route = NetworkRoute("cell", false)
        call(client).close()
        assertEquals("external.test", seen.last().url.host)
    }
    @Test fun `connection timeout falls back and cooldown resets on network change and expiry`() {
        val client = client {
            if (it.url.host == "internal.test") throw SocketTimeoutException("connect")
            200
        }
        call(client, "POST").close()
        assertEquals(listOf("internal.test", "external.test"), seen.map { it.url.host })
        seen.clear(); call(client).close()
        assertEquals(listOf("external.test"), seen.map { it.url.host })
        route = NetworkRoute("wifi2", true)
        seen.clear(); call(client).close()
        assertEquals(listOf("internal.test", "external.test"), seen.map { it.url.host })
        time = 30_001
        seen.clear(); call(client).close()
        assertEquals(2, seen.size)
    }
    @Test fun `cellular falls back to internal and single address is usable`() {
        route = NetworkRoute("cell", false)
        call(client { if (it.url.host == "external.test") throw IOException("offline") else 200 }).close()
        assertEquals(listOf("external.test", "internal.test"), seen.map { it.url.host })
        seen.clear()
        call(client(cfg.copy(internalServerUrl = "")) { 200 }).close()
        assertEquals(listOf("external.test"), seen.map { it.url.host })
        assertEquals(cfg.internalServerUrl, cfg.copy(externalServerUrl = "").serverUrl)
    }
    @Test fun `HTTP errors do not trigger failover`() {
        for (status in listOf(401, 403, 404, 503)) {
            seen.clear()
            call(client { status }).use { assertEquals(status, it.code) }
            assertEquals(1, seen.size)
        }
    }
    @Test fun `sent writes and TLS failures are never replayed`() {
        for (tls in listOf(false, true)) {
            seen.clear()
            val client = client {
                if (tls) throw SSLHandshakeException("certificate")
                it.tag(RequestAttempt::class.java)!!.sending = true
                throw SocketTimeoutException("read")
            }
            assertThrows(IOException::class.java) { call(client, "POST") }
            assertEquals(1, seen.size)
        }
    }
    @Test fun `both failed endpoints return failure with first error retained`() {
        val error = assertThrows(IOException::class.java) { call(client { throw IOException(it.url.host) }) }
        assertEquals("external.test", error.message)
        assertEquals("internal.test", error.suppressed.single().message)
    }
    @Test fun `unrelated image hosts are not rewritten`() {
        val client = client { 200 }
        client.newCall(Request.Builder().url("https://other.test/image.jpg").build()).execute().close()
        assertEquals("other.test", seen.single().url.host)
    }
    @Test fun `real connection refusal falls back before sending a write`() {
        java.net.ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val unavailablePort = java.net.ServerSocket(0).use { it.localPort }
            val actual = cfg.copy(externalServerUrl = "http://127.0.0.1:${server.localPort}",
                internalServerUrl = "http://127.0.0.1:$unavailablePort")
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                val received = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val firstLine = reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) { /* headers */ }
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                        firstLine
                    }
                }
                val client = OkHttpClient.Builder()
                    .addInterceptor(ServerRoutingInterceptor({ actual }, { route }))
                    .addNetworkInterceptor(trackServerRequest).build()
                client.newCall(Request.Builder().url("${actual.externalServerUrl}/api/v1/assets")
                    .post("data".toRequestBody()).build()).execute().use {
                    assertEquals(200, it.code)
                    assertEquals("ok", it.body!!.string())
                }
                assertEquals("POST /api/v1/assets HTTP/1.1", received.get(5, java.util.concurrent.TimeUnit.SECONDS))
            } finally {
                executor.shutdownNow()
            }
        }
    }
    @Test fun `server address validation allows one endpoint but rejects invalid settings`() {
        validateServerUrls("", "http://192.168.0.2:8080")
        validateServerUrls("https://photo.example.com", "")
        for ((external, internal) in listOf("" to "", "192.168.0.2" to "", "https://a/?key=x" to "", "https://a" to "bad")) {
            assertThrows(IllegalArgumentException::class.java) { validateServerUrls(external, internal) }
        }
    }
}
