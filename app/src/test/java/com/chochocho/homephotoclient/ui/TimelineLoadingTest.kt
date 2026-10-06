package com.chochocho.homephotoclient.ui

import com.chochocho.homephotoclient.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TimelineLoadingTest {
    private val unused = Retrofit.Builder().baseUrl("https://homephoto.test/")
        .addConverterFactory(GsonConverterFactory.create()).build().create(HomePhotoApi::class.java)
    private fun photo(id: Long, month: String) = AssetDto(id, "hash$id", "PHOTO", null,
        "$month-01T12:00:00", null, month, null, null)
    private fun api(
        monthReply: suspend () -> List<MonthDto> = { emptyList() },
        pageReply: suspend (String?, String?) -> AssetPageDto = { _, _ -> AssetPageDto(emptyList(), null) },
    ) = object : HomePhotoApi by unused {
        override suspend fun months() = monthReply()
        override suspend fun assets(cursor: String?, limit: Int, yearMonth: String?, clusterId: Int?): AssetPageDto {
            assertEquals(200, limit)
            return pageReply(cursor, yearMonth)
        }
    }

    @Test fun `month list includes older months before their photos are loaded`() = runBlocking {
        val service = api(monthReply = { listOf(MonthDto("2026-10", 100), MonthDto("2026-09", 100),
            MonthDto("2026-08", 300), MonthDto("2025-10", 400)) },
            pageReply = { _, _ -> AssetPageDto(listOf(photo(1, "2026-10")), null) })
        val months = TimelineMonthsState(service)
        val photos = AssetListState(service)
        months.load(); photos.loadMore()
        assertEquals(listOf("2026-10", "2026-09", "2026-08", "2025-10"), months.months)
        assertEquals(listOf("2026-10"), photos.items.map { it.yearMonth })
    }
    @Test fun `selected month persists across cursor pages and ends without extra requests`() = runBlocking {
        val requests = mutableListOf<Pair<String?, String?>>()
        val state = AssetListState(api(pageReply = { cursor, month ->
            requests += cursor to month
            if (cursor == null) AssetPageDto(listOf(photo(2, "2025-08")), "next")
            else AssetPageDto(listOf(photo(1, "2025-08")), null)
        }), yearMonth = "2025-08")
        repeat(3) { state.loadMore() }
        assertEquals(listOf(null to "2025-08", "next" to "2025-08"), requests)
        assertEquals(listOf(2L, 1L), state.items.map { it.id })
    }
    @Test fun `all mode continues from October through earlier months`() = runBlocking {
        var page = 0
        val state = AssetListState(api(pageReply = { cursor, month ->
            assertNull(month)
            if (page > 0) assertEquals("page$page", cursor)
            val monthValue = listOf("2026-10", "2026-09", "2026-08")[page]
            page++
            AssetPageDto(listOf(photo(page.toLong(), monthValue)), if (page == 3) null else "page$page")
        }))
        repeat(4) { state.loadMore() }
        assertEquals(3, page)
        assertEquals(listOf("2026-10", "2026-09", "2026-08"), buildCells(state.items, 4).filterIsInstance<Cell.Header>().map { it.yearMonth })
    }
    @Test fun `refresh discards an older response without ending the current loading state`() = runBlocking {
        val old = CompletableDeferred<AssetPageDto>()
        val fresh = CompletableDeferred<AssetPageDto>()
        var count = 0
        val state = AssetListState(api(pageReply = { _, _ -> if (count++ == 0) old.await() else fresh.await() }))
        val first = launch(start = CoroutineStart.UNDISPATCHED) { state.loadMore() }
        state.reset()
        val second = launch(start = CoroutineStart.UNDISPATCHED) { state.loadMore() }
        old.complete(AssetPageDto(listOf(photo(1, "2026-09")), "stale")); first.join()
        assertTrue(state.items.isEmpty()); assertTrue(state.loading)
        fresh.complete(AssetPageDto(listOf(photo(2, "2026-10")), null)); second.join()
        assertEquals(listOf(2L), state.items.map { it.id }); assertFalse(state.loading)
    }
    @Test fun `cancelled screen does not swallow cancellation or leave loading set`() = runBlocking {
        val state = AssetListState(api(pageReply = { _, _ -> throw CancellationException("screen changed") }))
        try { state.loadMore(); fail("expected cancellation") } catch (_: CancellationException) { }
        assertFalse(state.loading); assertNull(state.error)
    }
    @Test fun `month list failure can retry and retains previously loaded choices`() = runBlocking {
        var fail = false
        val state = TimelineMonthsState(api(monthReply = {
            if (fail) throw IllegalStateException("월 조회 실패")
            listOf(MonthDto("2025-01", 5))
        }))
        state.load(); fail = true; state.load()
        assertEquals(listOf("2025-01"), state.months)
        assertEquals("월 조회 실패", state.error); assertFalse(state.loading)
        fail = false; state.load(); assertNull(state.error)
    }
    @Test fun `year filter paginates within each month and skips an emptied month`() = runBlocking {
        val requests = mutableListOf<Pair<String?, String?>>()
        val state = AssetListState(api(pageReply = { cursor, month ->
            requests += cursor to month
            when (month) {
                "2026-10" -> if (cursor == null) AssetPageDto(listOf(photo(3, month)), "oct-next")
                    else AssetPageDto(listOf(photo(2, month)), null)
                "2026-09" -> AssetPageDto(emptyList(), null)
                "2026-08" -> AssetPageDto(listOf(photo(1, month)), null)
                else -> error("unexpected month")
            }
        }), yearMonths = listOf("2026-10", "2026-09", "2026-08"))
        repeat(4) { state.loadMore() }
        assertEquals(listOf(null to "2026-10", "oct-next" to "2026-10", null to "2026-09", null to "2026-08"), requests)
        assertEquals(listOf(3L, 2L, 1L), state.items.map { it.id })
        state.reset(); state.loadMore()
        assertEquals(null to "2026-10", requests.last())
        assertEquals(listOf(3L), state.items.map { it.id })
    }
    @Test fun `year without available months never falls back to all photos`() = runBlocking {
        val state = AssetListState(api(pageReply = { _, _ -> error("must not request all photos") }), yearMonths = emptyList())
        state.loadMore(); state.reset(); state.loadMore()
        assertTrue(state.items.isEmpty()); assertFalse(state.loading)
    }
    @Test fun `year and month selections produce independent choices and request scope`() {
        val available = listOf("2025-12", "2026-08", "2026-10", "2025-08")
        val all = TimelinePeriodFilter(available, null, null)
        assertEquals(listOf("2026", "2025"), all.years)
        assertNull(all.yearMonths)
        val year = TimelinePeriodFilter(available, "2026", null)
        assertEquals(listOf("10", "08"), year.months)
        assertEquals(listOf("2026-10", "2026-08"), year.yearMonths)
        assertEquals(listOf("2025-08"), TimelinePeriodFilter(available, "2025", "08").yearMonths)
        assertEquals(listOf("12", "08"), TimelinePeriodFilter(available, "2025", null).months)
    }
}
