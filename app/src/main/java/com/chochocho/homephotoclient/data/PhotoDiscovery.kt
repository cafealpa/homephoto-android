package com.chochocho.homephotoclient.data

import java.time.YearMonth
import retrofit2.HttpException

/** 문장 검색은 서버의 관련도 순서와 최대 24장 계약을 그대로 유지한다. */
internal data class PhotoQuery(val text: String = "", val yearMonth: String? = null) {
    val keyword: String get() = text.trim()
    val semantic: Boolean get() = keyword.isNotEmpty()
    val month: YearMonth? get() = yearMonth?.let(YearMonth::parse)
    val validationMessage: String? get() = if (keyword.length > 500) "검색어는 500자 이내로 입력해 주세요." else null
}

internal data class DiscoveryPage(
    val items: List<AssetDto>,
    val nextCursor: String?,
    val indexedPhotos: Long? = null,
)

internal suspend fun HomePhotoApi.discover(query: PhotoQuery, cursor: String? = null): DiscoveryPage {
    require(query.validationMessage == null) { query.validationMessage!! }
    if (query.semantic) {
        val result = searchPhotos(query.keyword, query.month?.atDay(1)?.toString(), query.month?.atEndOfMonth()?.toString())
        return DiscoveryPage(result.items, null, result.indexedPhotos)
    }
    val result = assets(cursor = cursor, limit = 60, yearMonth = query.yearMonth)
    return DiscoveryPage(result.items, result.nextCursor)
}

internal fun Throwable.toSearchMessage(): String = when {
    this is HttpException && code() == 503 -> "사진 검색 서비스를 사용할 수 없어요. PC 서버의 검색 서비스를 실행한 뒤 다시 시도해 주세요. 날짜별·인물별 보기는 계속 사용할 수 있어요."
    this is HttpException && code() == 404 -> "이 서버는 문장 검색을 지원하지 않아요. 서버를 업데이트하거나 날짜별·인물별 보기를 이용해 주세요."
    else -> toFriendlyMessage()
}
