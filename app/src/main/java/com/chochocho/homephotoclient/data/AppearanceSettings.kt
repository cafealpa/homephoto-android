package com.chochocho.homephotoclient.data

/** 표시 이름과 별도인 ID를 저장해서 이름 변경 후에도 사용자의 선택을 유지한다. */
enum class AppPalette(val id: String, val title: String) {
    PEACH("peach", "피치 크림"), SAGE("sage", "세이지 가든"),
    BLUE("blue", "클라우드 블루"), LAVENDER("lavender", "라벤더 미스트"),
    SAND("sand", "샌드 라테"), MONO("mono", "실버 모노");

    companion object {
        fun fromStored(value: String?): AppPalette = entries.firstOrNull { it.id == value } ?: PEACH
    }
}

enum class AppColorMode(val id: String, val title: String) {
    SYSTEM("system", "시스템 설정 따르기"), LIGHT("light", "노멀"), DARK("dark", "다크");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(value: String?): AppColorMode = entries.firstOrNull { it.id == value } ?: SYSTEM
    }
}

data class AppearanceSettings(
    val palette: AppPalette = AppPalette.PEACH,
    val mode: AppColorMode = AppColorMode.SYSTEM,
)
