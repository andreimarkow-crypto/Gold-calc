package com.example.goldcalculator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import org.jsoup.HttpStatusException

@OptIn(ExperimentalCoroutinesApi::class)
class GoldViewModelTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun failedRefreshPreservesQuoteAndTimestampAndCanRecover() = runBlocking {
        val calls = AtomicInteger()
        val model = GoldViewModel(GoldPriceRepository(loadDocument = {
            if (calls.incrementAndGet() == 2) throw HttpStatusException("Forbidden", 403, "https://www.profinance.ru")
            Jsoup.parse("<table><tr><td>XAURUB_FX</td><td>1</td><td>2</td><td>10942.06</td></tr></table>")
        }, pause = {}))
        val initial = withTimeout(5000) { model.uiState.first { !it.isLoading } }
        assertEquals(10942.06, initial.pricePerGram!!, 0.001)
        assertNotNull(initial.lastUpdated)
        model.fetchPrice()
        val failed = withTimeout(5000) { model.uiState.first { !it.isLoading } }
        assertEquals(initial.pricePerGram, failed.pricePerGram)
        assertEquals(initial.lastUpdated, failed.lastUpdated)
        assertTrue(failed.errorMessage!!.contains("403"))
        model.fetchPrice()
        val recovered = withTimeout(5000) { model.uiState.first { !it.isLoading } }
        assertNull(recovered.errorMessage)
        assertEquals(3, calls.get())
    }

    @Test fun repeatedRefreshDoesNotStartConcurrentRequests() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        val calls = AtomicInteger()
        val model = GoldViewModel(GoldPriceRepository(loadDocument = {
            calls.incrementAndGet()
            entered.complete(Unit)
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            Jsoup.parse("<table><tr><td>XAURUB_FX</td><td>1</td><td>2</td><td>10942.06</td></tr></table>")
        }, pause = {}))
        try {
            withTimeout(5000) { entered.await() }
            model.fetchPrice()
            model.fetchPrice()
            assertEquals(1, calls.get())
        } finally {
            release.countDown()
        }
        withTimeout(5000) { model.uiState.first { !it.isLoading } }
    }
}
