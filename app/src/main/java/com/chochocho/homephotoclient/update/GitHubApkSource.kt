package com.chochocho.homephotoclient.update

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal class GitHubApkSource(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(15, TimeUnit.MINUTES).followSslRedirects(false).build()) {
    fun latest(installed: Long): ApkRelease? {
        val request = Request.Builder().url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "HomePhoto-Android-Updater").build()
        val json = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { when (response.code) {
                403, 429 -> "GitHub 조회 한도에 도달했어요. 잠시 후 다시 확인해 주세요."
                404 -> "공개 릴리즈 저장소를 찾을 수 없어요. 배포 설정을 확인해 주세요."
                else -> "릴리즈를 확인할 수 없어요 (HTTP ${response.code})."
            } }
            val bytes = response.body!!.byteStream().use { it.readBytesLimited(4 * 1024 * 1024) }
            String(bytes, Charsets.UTF_8)
        }
        return select(json, installed)
    }
    fun openApk(release: ApkRelease): InputStream {
        release.validate(PACKAGE)
        require(release.apkUrl.startsWith("https://github.com/$REPOSITORY/releases/download/")) { "허용되지 않은 APK 주소예요." }
        val response = client.newCall(Request.Builder().url(release.apkUrl).build()).execute()
        if (!response.isSuccessful) { val code = response.code; response.close(); error("APK를 받을 수 없어요 (HTTP $code). 새 버전을 다시 확인해 주세요.") }
        val body = response.body ?: run { response.close(); error("APK 응답이 비어 있어요.") }
        if (body.contentLength() != -1L && body.contentLength() != release.sizeBytes) {
            response.close(); error("APK 크기가 릴리즈 정보와 달라요.")
        }
        return body.byteStream() // stream.close()가 응답 본문과 연결을 반환한다.
    }
    companion object {
        const val REPOSITORY = "cafealpa/homephoto-android"
        const val RELEASES_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100"
        const val PACKAGE = "com.chochocho.homephotoclient"
        private val assetName = Regex("homephoto-android-([1-9][0-9]*)\\.apk")
        internal fun select(json: String, installed: Long): ApkRelease? {
            val candidates = JsonParser.parseString(json).asJsonArray.flatMap { element ->
                val release = element.asJsonObject
                if (release["draft"].asBoolean || release["prerelease"].asBoolean) return@flatMap emptyList()
                release["assets"].asJsonArray.mapNotNull { item ->
                    val asset = item.asJsonObject
                    if (asset["state"].asString != "uploaded") return@mapNotNull null
                    val code = assetName.matchEntire(asset["name"].asString)?.groupValues?.get(1)?.toLongOrNull()
                        ?: return@mapNotNull null
                    if (code <= installed) return@mapNotNull null
                    Triple(code, release, asset)
                }
            }
            val (code, release, asset) = candidates.maxByOrNull { it.first } ?: return null
            val digest = asset["digest"]?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            require(digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) { "릴리즈의 SHA-256 정보가 없어요. 배포 파일을 확인해 주세요." }
            val url = asset["browser_download_url"].asString
            require(url.startsWith("https://github.com/$REPOSITORY/releases/download/")) { "허용되지 않은 APK 주소예요." }
            return ApkRelease(PACKAGE, code, release["tag_name"].asString, url, digest.removePrefix("sha256:"),
                asset["size"].asLong, notes = release["body"]?.takeUnless { it.isJsonNull }?.asString.orEmpty().take(10000)).validate(PACKAGE)
        }
    }
}

private fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer); if (count < 0) return output.toByteArray()
        require(output.size() + count <= limit) { "릴리즈 응답이 너무 커요." }
        output.write(buffer, 0, count)
    }
}
