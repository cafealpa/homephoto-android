package com.chochocho.homephotoclient.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class UpdateState(val release: ApkRelease? = null, val busy: Boolean = false, val ready: Boolean = false,
                       val percent: Int? = null, val message: String = "새 버전이 있는지 확인해 보세요.", val error: Boolean = false)

/** 앱 프로세스 범위에서 한 번만 다운로드한다. 화면 전환/회전으로 다운로드가 중복되지 않는다. */
class AppUpdater private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val source = GitHubApkSource()
    private val cache = ApkCache(File(context.filesDir, "updates"), context.packageName)
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    val installedVersion: Long get() = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    val installedName: String get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    init {
        run { restore() }
    }
    private fun restore() = work {
        val release = cache.restore(GitHubApkSource.RELEASES_URL, installedVersion)
        val ready = release != null && cache.valid(release)
        mutable.value = UpdateState(release, ready = ready, message = if (ready) "받아둔 APK가 있어요. 인터넷 연결 없이 다시 설치할 수 있어요." else "새 버전이 있는지 확인해 보세요.")
    }
    fun check() = work {
        val release = source.latest(installedVersion)
        if (release == null) {
            mutable.value = mutable.value.copy(busy = false, message = "현재 버전보다 새로운 정식 APK 릴리즈가 없어요.", error = false)
        } else {
            cache.remember(GitHubApkSource.RELEASES_URL, release)
            val ready = cache.valid(release)
            mutable.value = UpdateState(release, ready = ready, message = if (ready) "이미 받은 APK예요. 바로 설치할 수 있어요." else "새 버전을 사용할 수 있어요.")
        }
    }
    fun download() = work {
        val release = mutable.value.release ?: error("먼저 새 버전을 확인해 주세요.")
        var last = -1
        cache.prepare(release, { source.openApk(release) }) { received ->
            val percent = (received * 100 / release.sizeBytes).toInt()
            if (percent != last) { last = percent; mutable.value = mutable.value.copy(percent = percent, message = "APK 다운로드 중…") }
        }
        validateArchive(cache.file(release), release)
        mutable.value = mutable.value.copy(busy = false, ready = true, percent = null, error = false, message = "다운로드와 검증이 끝났어요. 설치 버튼을 눌러 주세요.")
    }
    fun install() = work {
        val release = mutable.value.release ?: error("먼저 새 버전을 확인해 주세요.")
        if (!cache.valid(release)) {
            mutable.value = mutable.value.copy(ready = false)
            error("보관된 APK가 없거나 손상됐어요. 다시 다운로드해 주세요.")
        }
        val file = cache.file(release)
        validateArchive(file, release)
        withContext(Dispatchers.Main) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                mutable.value = mutable.value.copy(busy = false, ready = true, message = "이 앱의 설치 허용을 켠 뒤 돌아와 ‘설치 / 다시 설치’를 눌러 주세요.")
            } else {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .apply { clipData = ClipData.newRawUri("HomePhoto APK", uri) })
                mutable.value = mutable.value.copy(busy = false, ready = true, message = "설치 화면을 열었어요. 실패하거나 취소했다면 같은 버튼으로 다시 설치할 수 있어요.")
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun validateArchive(file: File, release: ApkRelease) {
        val pm = context.packageManager
        val apk = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("올바른 APK 파일이 아니에요. 배포 파일을 확인해 주세요.")
        require(apk.packageName == context.packageName && apk.longVersionCode == release.versionCode) { "APK의 앱 이름이나 버전이 릴리즈 정보와 달라요." }
        require(apk.longVersionCode > installedVersion) { "이미 설치된 버전이거나 이전 버전이에요." }
        require((apk.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "이 버전은 더 높은 Android 버전이 필요해요." }
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val current = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val incoming = apk.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        require(current.isNotEmpty() && current == incoming) { "설치된 앱과 APK의 서명이 달라요. 같은 서명 키로 만든 배포 파일이 필요해요." }
    }
    @Synchronized private fun work(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, percent = null, error = false)
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = if (e is java.io.IOException) "업데이트에 연결할 수 없어요. 인터넷 연결을 확인해 주세요. 받은 APK는 보관돼요." else e.message ?: "업데이트를 처리할 수 없어요."
                mutable.value = mutable.value.copy(message = message, error = true)
            } finally { mutable.value = mutable.value.copy(busy = false, percent = null) }
        }
    }
    companion object {
        @Volatile private var instance: AppUpdater? = null
        fun get(context: Context): AppUpdater = instance ?: synchronized(this) {
            instance ?: AppUpdater(context.applicationContext).also { instance = it }
        }
    }
}
