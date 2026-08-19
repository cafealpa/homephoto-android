package com.chochocho.homephotoclient.ui

import com.chochocho.homephotoclient.data.AssetDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1c 확장의 대표 타일(2×2) 묶음 규칙 검증.
 * 화면에 사진이 없으면 눈으로 확인할 수 없는 부분이라 로직만 따로 테스트한다.
 */
class TimelineCellsTest {

    private fun asset(id: Long, ym: String?) = AssetDto(
        id = id,
        hash = "h$id",
        mediaType = "IMAGE",
        originalFilename = "IMG_$id.jpg",
        takenAt = null,
        takenAtSource = null,
        yearMonth = ym,
        width = null,
        height = null,
    )

    @Test
    fun `4열이면 첫 월의 앞 5장이 대표 타일 하나로 묶인다`() {
        val items = (1L..8L).map { asset(it, "2026-08") }

        val cells = buildCells(items, FEATURED_COLUMNS)

        assertEquals(Cell.Header("2026-08"), cells[0])
        val featured = cells[1] as Cell.Featured
        assertEquals(FEATURED_TAKE, featured.assets.size)
        assertEquals(0, featured.startIndex)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), featured.assets.map { it.id })
        // 남은 3장은 일반 셀
        assertEquals(listOf(6L, 7L, 8L), cells.drop(2).map { (it as Cell.Photo).asset.id })
    }

    @Test
    fun `대표 타일은 한 번만 만들어진다`() {
        val items = (1L..6L).map { asset(it, "2026-08") } + (7L..12L).map { asset(it, "2026-07") }

        val cells = buildCells(items, FEATURED_COLUMNS)

        assertEquals(1, cells.count { it is Cell.Featured })
    }

    @Test
    fun `4열이 아니면 대표 타일을 만들지 않는다`() {
        val items = (1L..8L).map { asset(it, "2026-08") }

        listOf(2, 3, 5, 6, 8).forEach { columns ->
            val cells = buildCells(items, columns)
            assertTrue("columns=$columns", cells.none { it is Cell.Featured })
            assertEquals("columns=$columns", 8, cells.count { it is Cell.Photo })
        }
    }

    @Test
    fun `월이 5장 미만이면 그 월은 대표 타일을 쓰지 않는다`() {
        // 첫 월이 3장뿐 — 월을 넘겨 묶지 않고 다음 월에서 대표 타일이 만들어진다
        val items = (1L..3L).map { asset(it, "2026-08") } + (4L..10L).map { asset(it, "2026-07") }

        val cells = buildCells(items, FEATURED_COLUMNS)

        val featured = cells.filterIsInstance<Cell.Featured>().single()
        assertEquals("2026-07", featured.yearMonth)
        assertEquals(listOf(4L, 5L, 6L, 7L, 8L), featured.assets.map { it.id })
        assertEquals(3, featured.startIndex)
    }

    @Test
    fun `월이 바뀔 때마다 헤더가 들어간다`() {
        val items = listOf(asset(1, "2026-08"), asset(2, "2026-07"), asset(3, "2026-07"))

        val cells = buildCells(items, columns = 3)

        assertEquals(
            listOf("2026-08", "2026-07"),
            cells.filterIsInstance<Cell.Header>().map { it.yearMonth },
        )
    }

    @Test
    fun `yearMonth 가 없으면 기타로 묶는다`() {
        val cells = buildCells(listOf(asset(1, null), asset(2, null)), columns = 3)

        assertEquals("기타", cells.filterIsInstance<Cell.Header>().single().yearMonth)
    }

    @Test
    fun `Photo 의 index 는 원본 목록 인덱스와 같다 — 뷰어 시작 위치로 쓰인다`() {
        val items = (1L..7L).map { asset(it, if (it <= 2) "2026-08" else "2026-07") }

        val cells = buildCells(items, columns = 4)

        cells.filterIsInstance<Cell.Photo>().forEach { photo ->
            assertEquals(photo.asset.id, items[photo.index].id)
        }
        cells.filterIsInstance<Cell.Featured>().forEach { f ->
            f.assets.forEachIndexed { offset, a ->
                assertEquals(a.id, items[f.startIndex + offset].id)
            }
        }
    }
}
