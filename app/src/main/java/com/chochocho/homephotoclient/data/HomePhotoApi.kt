package com.chochocho.homephotoclient.data

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

data class MonthDto(val yearMonth: String, val count: Long)
data class AssetDto(
    val id: Long,
    val hash: String,
    val mediaType: String?,
    val originalFilename: String?,
    val takenAt: String?,
    val takenAtSource: String?,
    val yearMonth: String?,
    val width: Int?,
    val height: Int?,
)
data class AssetPageDto(val items: List<AssetDto>, val nextCursor: String?)
data class PhotoSearchDto(
    val items: List<AssetDto>,
    @com.google.gson.annotations.SerializedName("indexed_photos") val indexedPhotos: Long,
    val approximate: Boolean,
)
data class CheckRequest(val hashes: List<String>)
data class CheckResponse(
    val missing: List<String>,
    /** 서버에서 삭제된(스킵 대상) 해시들 */
    val deleted: List<String>?,
    val queued: List<String>? = null,
)
data class UploadReceipt(val hash: String, val status: String?)

/** 구형 서버는 queued/deleted를 생략할 수 있다. 수신 대기를 원본 저장 완료와 구분한다. */
fun CheckResponse.storedHashes(requested: List<String>): List<String> {
    val excluded = (missing + deleted.orEmpty() + queued.orEmpty()).toSet()
    return requested.filterNot { it in excluded }
}
data class ClusterDto(val clusterId: Int, val faceCount: Long, val coverFaceId: Long, val name: String?)
data class NameClusterRequest(val name: String)

data class BackupCapacity(val accepting: Boolean, val reason: String?, val retryAfterSeconds: Int = 300)

data class FamilyDevice(val id: String?, val name: String, val count: Int)
data class FamilyAlbumSummary(val kind: String, val date: String, val endDate: String, val title: String,
    val albumId: Long?, val coverAssetId: Long?, val photoCount: Int, val deviceCount: Int)
data class FamilyAlbumDetail(val summary: FamilyAlbumSummary, val note: String, val revision: Int,
    val selected: List<AssetDto>, val devices: List<FamilyDevice>)
data class FamilyAlbumHome(val weekly: List<FamilyAlbumSummary>, val together: List<FamilyAlbumSummary>, val saved: List<FamilyAlbumSummary>)
data class SaveFamilyAlbum(val title: String, val note: String, val assetIds: List<Long>, val revision: Int)

data class MemorySummary(val id: Long, val title: String, val startDate: String, val endDate: String,
    val coverAssetId: Long?, val photoCount: Int, val albumId: Long?)
data class MemoryDetail(val summary: MemorySummary, val photos: List<AssetDto>)
data class MemoryHome(val date: String, val memories: List<MemorySummary>, val saved: List<MemorySummary>)
data class SaveMemoryAlbum(val title: String, val assetIds: List<Long>)

interface HomePhotoApi {
    @GET("api/v1/memories/home")
    suspend fun memoriesHome(): MemoryHome
    @GET("api/v1/memories/{id}")
    suspend fun memory(@retrofit2.http.Path("id") id: Long): MemoryDetail
    @GET("api/v1/memories/albums/{id}")
    suspend fun memoryAlbum(@retrofit2.http.Path("id") id: Long): MemoryDetail
    @POST("api/v1/memories/{id}/album")
    suspend fun saveMemoryAlbum(@retrofit2.http.Path("id") id: Long, @Body request: SaveMemoryAlbum): MemoryDetail

    @GET("api/v1/family-albums/home")
    suspend fun familyAlbumsHome(): FamilyAlbumHome
    @GET("api/v1/family-albums/{kind}/{date}")
    suspend fun familyAlbum(@retrofit2.http.Path("kind") kind: String, @retrofit2.http.Path("date") date: String): FamilyAlbumDetail
    @GET("api/v1/family-albums/{kind}/{date}/candidates")
    suspend fun familyAlbumCandidates(@retrofit2.http.Path("kind") kind: String, @retrofit2.http.Path("date") date: String,
        @retrofit2.http.Query("cursor") cursor: String? = null): AssetPageDto
    @POST("api/v1/family-albums/{kind}/{date}")
    suspend fun saveFamilyAlbum(@retrofit2.http.Path("kind") kind: String, @retrofit2.http.Path("date") date: String,
        @Body request: SaveFamilyAlbum): FamilyAlbumDetail

    @GET("api/v1/backup-capacity")
    suspend fun backupCapacity(@retrofit2.http.Query("bytes") bytes: Long): Response<BackupCapacity>
    @GET("api/v1/photos/search")
    suspend fun searchPhotos(
        @retrofit2.http.Query("query") query: String,
        @retrofit2.http.Query("start_date") startDate: String? = null,
        @retrofit2.http.Query("end_date") endDate: String? = null,
        @retrofit2.http.Query("limit") limit: Int = 24,
    ): PhotoSearchDto

    @GET("api/v1/months")
    suspend fun months(): List<MonthDto>

    @GET("api/v1/assets")
    suspend fun assets(
        @retrofit2.http.Query("cursor") cursor: String? = null,
        @retrofit2.http.Query("limit") limit: Int = 100,
        @retrofit2.http.Query("yearMonth") yearMonth: String? = null,
        @retrofit2.http.Query("clusterId") clusterId: Int? = null,
    ): AssetPageDto

    @GET("api/v1/faces/clusters")
    suspend fun clusters(): List<ClusterDto>

    @POST("api/v1/faces/clusters/{clusterId}/name")
    suspend fun nameCluster(
        @retrofit2.http.Path("clusterId") clusterId: Int,
        @Body request: NameClusterRequest,
    ): Map<String, Any>

    @retrofit2.http.DELETE("api/v1/assets/{id}")
    suspend fun deleteAsset(@retrofit2.http.Path("id") id: Long): Map<String, Any>

    @POST("api/v1/assets/check")
    suspend fun check(@Body request: CheckRequest): CheckResponse

    /** 202 = 서버 수신 완료(원본 저장 대기), 201/409 = 원본 저장 완료 */
    @retrofit2.http.Headers("X-Upload-Queue: true")
    @Multipart
    @POST("api/v1/assets")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("hash") hash: RequestBody,
        @Part("fileMtime") fileMtime: RequestBody?,
    ): Response<UploadReceipt>
}

object ApiFactory {

    fun create(
        baseUrl: String,
        apiKey: String,
        deviceId: String? = null,
        deviceName: String? = null,
        routing: okhttp3.Interceptor? = null,
    ): HomePhotoApi {
        val client = OkHttpClient.Builder()
            .apply { if (routing != null) addInterceptor(routing) }
            .addNetworkInterceptor(trackServerRequest)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.MINUTES) // 대용량 동영상 업로드 대비
            .addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                }
            )
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("X-Api-Key", apiKey)
                if (deviceId != null) {
                    builder.header("X-Device-Id", deviceId)
                    // 한글 기기명은 HTTP 헤더에 못 실리므로 URL 인코딩
                    builder.header(
                        "X-Device-Name",
                        java.net.URLEncoder.encode(deviceName ?: deviceId, "UTF-8"),
                    )
                }
                chain.proceed(builder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HomePhotoApi::class.java)
    }
}
