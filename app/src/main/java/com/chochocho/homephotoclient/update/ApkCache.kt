package com.chochocho.homephotoclient.update

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class ApkRelease(
    val packageName: String, val versionCode: Long, val versionName: String,
    val apkUrl: String, val sha256: String, val sizeBytes: Long,
    val minSdk: Int = 31, val notes: String = "",
) {
    fun validate(expectedPackage: String): ApkRelease {
        require(packageName == expectedPackage) { "HomePhoto용 업데이트가 아니에요." }
        require(versionCode > 0 && versionName.isNotBlank() && versionName.length <= 100) { "버전 정보가 올바르지 않아요." }
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "APK 검증 정보가 올바르지 않아요." }
        require(sizeBytes in 1..MAX_APK_BYTES && minSdk > 0 && notes.length <= 10000) { "APK 크기 또는 안내 정보가 올바르지 않아요." }
        validateUpdateUrl(apkUrl)
        return this
    }
    companion object { const val MAX_APK_BYTES = 500L * 1024 * 1024 }
}

internal fun validateUpdateUrl(value: String) {
    val url = value.toHttpUrlOrNull()
    require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) {
        "업데이트 주소는 HTTPS 주소로 입력해 주세요."
    }
}

internal data class PendingUpdate(val source: String, val release: ApkRelease)

/** 완료 파일만 재사용한다. 중단된 .part 파일은 다음 시도에서 처음부터 받는다. */
internal class ApkCache(private val root: File, private val packageName: String) {
    private val apkDir = File(root, "apk").apply { mkdirs() }
    private val metadata = File(root, "pending.json")
    private val gson = Gson()
    fun file(release: ApkRelease): File {
        release.validate(packageName)
        return File(apkDir, "update-${release.versionCode}-${release.sha256.lowercase()}.apk")
    }
    fun valid(release: ApkRelease): Boolean {
        val file = file(release)
        return file.isFile && file.length() == release.sizeBytes && sha256(file).equals(release.sha256, true)
    }
    fun remember(source: String, release: ApkRelease) {
        validateUpdateUrl(source); release.validate(packageName)
        val temp = File(root, "pending.tmp")
        temp.writeText(gson.toJson(PendingUpdate(source, release)))
        Files.move(temp.toPath(), metadata.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    fun restore(source: String, installed: Long): ApkRelease? {
        // 설치 완료는 OS의 versionCode로 판단한다. 설치 화면을 열었다고 지우지 않는다.
        apkDir.listFiles()?.filter { it.name.startsWith("update-") && it.name.substringAfter("update-").substringBefore('-').toLongOrNull()?.let { code -> code <= installed } == true }
            ?.forEach { it.delete() }
        if (!metadata.isFile) return null
        return try {
            val saved = gson.fromJson(metadata.readText(), PendingUpdate::class.java)
            saved.release.validate(packageName)
            if (saved.release.versionCode <= installed) { metadata.delete(); null }
            else saved.release.takeIf { saved.source == source }
        } catch (_: Exception) { null }
    }
    fun prepare(release: ApkRelease, open: () -> InputStream, progress: (Long) -> Unit = {}): File {
        val target = file(release)
        if (valid(release)) return target // 네트워크를 열기 전에 검증한다.
        target.delete()
        val partial = File(apkDir, target.name + ".part")
        try {
            var received = 0L
            open().use { input -> partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count
                    require(received <= release.sizeBytes) { "APK 크기가 배포 정보와 달라요. 다시 확인해 주세요." }
                    output.write(buffer, 0, count); progress(received)
                }
            } }
            require(received == release.sizeBytes && sha256(partial).equals(release.sha256, true)) { "다운로드한 APK 검증에 실패했어요. 다시 받아 주세요." }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            apkDir.listFiles()?.filter { it != target && (it.name.endsWith(".apk") || it.name.endsWith(".part")) }?.forEach { it.delete() }
            return target
        } finally { partial.delete() }
    }
    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
