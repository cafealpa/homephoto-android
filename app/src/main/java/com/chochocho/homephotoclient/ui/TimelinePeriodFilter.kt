package com.chochocho.homephotoclient.ui

/** 전체/연도/월 선택을 기존 yearMonth 조회 API의 월 목록으로 변환한다. null은 전체 사진이다. */
internal class TimelinePeriodFilter(available: List<String>, year: String?, month: String?) {
    private val availableMonths = available.distinct().sortedDescending()
    val years = availableMonths.map { it.substringBefore('-') }.distinct()
    val months = availableMonths.filter { it.substringBefore('-') == year }.map { it.substringAfter('-') }
    val yearMonths: List<String>? = when {
        year == null -> null
        month == null -> availableMonths.filter { it.substringBefore('-') == year }
        else -> listOf("$year-$month")
    }
}
