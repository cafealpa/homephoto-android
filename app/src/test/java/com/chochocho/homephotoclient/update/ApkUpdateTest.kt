package com.chochocho.homephotoclient.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest

class ApkUpdateTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = "complete-apk-fixture".toByteArray()
    private fun release(code: Long = 2) = ApkRelease(GitHubApkSource.PACKAGE, code, "v1.1",
        "https://github.com/${GitHubApkSource.REPOSITORY}/releases/download/v1.1/homephoto-android-$code.apk",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong())
    private fun cache() = ApkCache(folder.root, GitHubApkSource.PACKAGE)
    @Test fun `completed file is reused without opening network after installer failure and process restart`() {
        var downloads = 0
        val release = release()
        cache().remember(GitHubApkSource.RELEASES_URL, release)
        val first = cache().prepare(release, { downloads++; ByteArrayInputStream(bytes) })
        // 설치 실패/취소는 파일을 건드리지 않는다. 새 객체로 앱 재시작을 재현한다.
        val restored = cache().restore(GitHubApkSource.RELEASES_URL, 1)!!
        val retry = cache().prepare(restored, { downloads++; throw IOException("offline") })
        assertEquals(first, retry); assertEquals(1, downloads); assertArrayEquals(bytes, retry.readBytes())
    }
    @Test fun `corrupt complete file is replaced even when size matches`() {
        val r = release(); val cache = cache()
        cache.file(r).writeBytes(ByteArray(bytes.size))
        var downloads = 0
        cache.prepare(r, { downloads++; ByteArrayInputStream(bytes) })
        assertEquals(1, downloads); assertTrue(cache.valid(r))
    }
    @Test fun `partial and wrong checksum downloads never become installable`() {
        val cache = cache(); val r = release()
        for (data in listOf(bytes.take(4).toByteArray(), ByteArray(bytes.size), bytes + byteArrayOf(1))) {
            assertThrows(IllegalArgumentException::class.java) { cache.prepare(r, { ByteArrayInputStream(data) }) }
            assertFalse(cache.valid(r)); assertFalse(folder.root.walkTopDown().any { it.name.endsWith(".part") })
        }
        cache.prepare(r, { ByteArrayInputStream(bytes) }); assertTrue(cache.valid(r))
    }
    @Test fun `failed newer download preserves previously downloaded version`() {
        val cache = cache(); cache.prepare(release(), { ByteArrayInputStream(bytes) })
        assertThrows(IOException::class.java) { cache.prepare(release(3), { throw IOException("offline") }) }
        assertTrue(cache.valid(release()))
    }
    @Test fun `installed version clears cached apk only after version has advanced`() {
        val cache = cache(); val r = release()
        cache.remember(GitHubApkSource.RELEASES_URL, r); cache.prepare(r, { ByteArrayInputStream(bytes) })
        assertNotNull(cache.restore(GitHubApkSource.RELEASES_URL, 1)); assertTrue(cache.file(r).exists())
        assertNull(cache.restore(GitHubApkSource.RELEASES_URL, 2)); assertFalse(cache.file(r).exists())
    }
    private fun json(code: Int, draft: Boolean = false, prerelease: Boolean = false, digest: String = "sha256:${release().sha256}") = """
        {"tag_name":"v$code","draft":$draft,"prerelease":$prerelease,"body":"변경 안내","assets":[
        {"name":"homephoto-android-$code.apk","state":"uploaded","size":${bytes.size},"digest":"$digest",
        "browser_download_url":"https://github.com/${GitHubApkSource.REPOSITORY}/releases/download/v$code/homephoto-android-$code.apk"}]}
    """
    @Test fun `highest stable version code wins over drafts prereleases and API order`() {
        val selected = GitHubApkSource.select("[${json(4)},${json(9, draft = true)},${json(8, prerelease = true)},${json(2)}]", 1)
        assertEquals(4L, selected!!.versionCode)
        assertNull(GitHubApkSource.select("[${json(2)}]", 2))
    }
    @Test fun `unverifiable newest release is rejected instead of silently using older release`() {
        assertThrows(IllegalArgumentException::class.java) { GitHubApkSource.select("[${json(3, digest = "")},${json(2)}]", 1) }
        assertThrows(IllegalArgumentException::class.java) { GitHubApkSource.select("[${json(3).replace("https://github.com/", "https://untrusted.test/")}]", 1) }
    }
    @Test fun `wrong package invalid size traversal hash and insecure urls are rejected`() {
        for (r in listOf(release().copy(packageName = "other"), release().copy(sizeBytes = 0), release().copy(sha256 = "../escape"), release().copy(apkUrl = "http://example.com/a.apk"))) {
            assertThrows(IllegalArgumentException::class.java) { cache().file(r) }
        }
    }
}
