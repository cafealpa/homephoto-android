package com.chochocho.homephotoclient.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.net.ssl.SSLException

internal fun validateServerUrls(external: String, internal: String) {
    require(external.isNotBlank() || internal.isNotBlank()) { "서버 주소를 하나 이상 입력해 주세요." }
    listOf(external, internal).filter { it.isNotBlank() }.forEach {
        val url = it.trim().toHttpUrlOrNull()
        require(url != null && url.username.isEmpty() && url.password.isEmpty() &&
            url.query == null && url.fragment == null) {
            "서버 주소는 http:// 또는 https://로 시작하고 사용자 정보·쿼리·#을 포함하지 않아야 합니다."
        }
    }
}

internal fun serverRouting(context: Context, settings: () -> AppSettings): ServerRoutingInterceptor {
    val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    return ServerRoutingInterceptor(settings, network = {
        val network = manager.activeNetwork
        NetworkRoute(network?.toString(), manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
    })
}

internal data class NetworkRoute(val id: String?, val wifi: Boolean)
internal class RequestAttempt { @Volatile var sending = false }

/** 연결이 성립된 이후 쓰기 요청을 재전송하여 앨범 저장 등을 중복 실행하지 않는다. */
internal val trackServerRequest = Interceptor { chain ->
    chain.request().tag(RequestAttempt::class.java)?.sending = true
    chain.proceed(chain.request())
}

internal class ServerRoutingInterceptor(
    private val settings: () -> AppSettings,
    private val network: () -> NetworkRoute,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) : Interceptor {
    private data class Failure(val network: NetworkRoute, val url: HttpUrl, val until: Long)
    @Volatile private var failure: Failure? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val cfg = settings()
        val current = network()
        val ordered = (if (current.wifi) listOf(cfg.internalServerUrl, cfg.externalServerUrl)
            else listOf(cfg.externalServerUrl, cfg.internalServerUrl))
            .filter { it.isNotBlank() }.map { (it.trimEnd('/') + "/").toHttpUrl() }.distinct()
        val request = chain.request()
        val source = ordered.firstOrNull { base ->
            request.url.scheme == base.scheme && request.url.host == base.host &&
                request.url.port == base.port && request.url.encodedPath.startsWith(base.encodedPath)
        } ?: return chain.proceed(request)
        // 접근 불가한 내부 주소 때문에 모든 썸네일/병렬 업로드가 연결 제한시간을 기다리지 않도록 한다.
        val failed = failure
        val candidates = ordered.sortedBy {
            if (failed?.network == current && failed.url == it && now() < failed.until) 1 else 0
        }
        var previous: IOException? = null
        for ((index, target) in candidates.withIndex()) {
            val attempt = RequestAttempt()
            val url = target.newBuilder()
                .encodedPath(target.encodedPath + request.url.encodedPath.removePrefix(source.encodedPath))
                .encodedQuery(request.url.encodedQuery).build()
            try {
                val response = chain.proceed(request.newBuilder().url(url)
                    .tag(RequestAttempt::class.java, attempt).build())
                if (failure?.url == target) failure = null
                return response
            } catch (error: IOException) {
                previous?.let { error.addSuppressed(it) }
                val safeToRetry = !attempt.sending || request.method in listOf("GET", "HEAD")
                if (chain.call().isCanceled() || error is SSLException || !safeToRetry ||
                    request.body?.isOneShot() == true || request.body?.isDuplex() == true ||
                    index == candidates.lastIndex) throw error
                failure = Failure(current, target, now() + 30_000)
                previous = error
            }
        }
        throw checkNotNull(previous)
    }
}
