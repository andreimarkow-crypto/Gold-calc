package com.example.goldcalculator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class GoldPriceRepositoryTest {
    private fun table(price: String = "10&#x202f;942,06", headers: String = "<th>Type</th><th>Bid</th><th>Ask</th><th>Last</th>") =
        Jsoup.parse("<table><thead><tr>$headers</tr></thead><tbody><tr><td>XAURUB_FX</td><td>10862.43</td><td>11021.70</td><td>$price</td></tr></tbody></table>")

    @Test fun parsesRealProfinanceTable() {
        val html = javaClass.getResource("/profinance-gold-table.html")!!.readText()
        assertEquals(10942.06, parseGoldPrice(Jsoup.parse(html)), 0.001)
    }

    @Test fun acceptsRussianAndEnglishPriceFormats() {
        for (price in listOf("10&#x202f;942,06", "10&nbsp;942,06", "10 942.06", "10942.06")) {
            assertEquals(10942.06, parseGoldPrice(table(price)), 0.001)
        }
    }

    @Test fun followsLastColumnWhenColumnsMove() {
        val document = table("1.25", "<th>Type</th><th>Last</th><th>Ask</th><th>Chg.</th>")
        assertEquals(10862.43, parseGoldPrice(document), 0.001)
    }

    @Test fun supportsLegacyTableWithoutHeaders() {
        assertEquals(10942.06, parseGoldPrice(table(headers = "")), 0.001)
    }

    @Test fun rejectsMissingInstrumentAndMisleadingSubstring() {
        for (html in listOf("<html>Service unavailable</html>", "<table><tr><td>NOT_XAURUB_FX</td><td>1</td><td>2</td><td>3</td></tr></table>")) {
            assertThrows(GoldPriceFormatException::class.java) { parseGoldPrice(Jsoup.parse(html)) }
        }
    }

    @Test fun rejectsInvalidPricesRatherThanUsingAnotherColumn() {
        for (price in listOf("—", "", "NaN", "Infinity", "0", "-1", "17:09:02", "1.2%", "1,2,3")) {
            assertThrows(GoldPriceFormatException::class.java) { parseGoldPrice(table(price)) }
        }
        assertThrows(GoldPriceFormatException::class.java) {
            parseGoldPrice(table(headers = "<th>Type</th><th>Bid</th><th>Ask</th><th>Time</th>"))
        }
    }

    @Test fun retries503ThenTimeoutAndSucceeds() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        val repository = GoldPriceRepository(loadDocument = {
            when (++calls) {
                1 -> throw HttpStatusException("Unavailable", 503, "https://www.profinance.ru")
                2 -> throw SocketTimeoutException()
                else -> table()
            }
        }, pause = { waits.add(it) })
        assertEquals(10942.06, repository.fetchPrice(), 0.001)
        assertEquals(3, calls)
        assertEquals(listOf(1000L, 2000L), waits)
    }

    @Test fun stopsAfterThreeTransientFailures() {
        var calls = 0
        assertThrows(SocketTimeoutException::class.java) {
            runBlocking {
                GoldPriceRepository(loadDocument = { calls++; throw SocketTimeoutException() }, pause = {}).fetchPrice()
            }
        }
        assertEquals(3, calls)
    }

    @Test fun doesNotRetryAccessDenialRateLimitCertificatesOrInvalidHtml() {
        for (error in listOf(
            HttpStatusException("Forbidden", 403, "https://www.profinance.ru"),
            HttpStatusException("Rate limited", 429, "https://www.profinance.ru"),
            SSLHandshakeException("Invalid certificate"),
            GoldPriceFormatException("Missing price")
        )) {
            var calls = 0
            val caught = assertThrows(error.javaClass) {
                runBlocking { GoldPriceRepository(loadDocument = { calls++; throw error }, pause = {}).fetchPrice() }
            }
            // Coroutines may copy exceptions to recover the asynchronous stack trace.
            assertEquals(error.message, caught.message)
            assertEquals(1, calls)
        }
    }

    @Test fun cancellationIsNotRetried() {
        var calls = 0
        assertThrows(CancellationException::class.java) {
            runBlocking { GoldPriceRepository(loadDocument = { calls++; throw CancellationException() }, pause = {}).fetchPrice() }
        }
        assertEquals(1, calls)
    }
}
