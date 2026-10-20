package com.example.goldcalculator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class GoldPriceRepository internal constructor(
    private val loadDocument: () -> Document = ::downloadGoldPage,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun fetchPrice(): Double = withContext(Dispatchers.IO) {
        for (attempt in 0..2) {
            try {
                return@withContext parseGoldPrice(loadDocument())
            } catch (error: IOException) {
                // Retry temporary transport/server failures, never certificate errors or access denial.
                val retryable = when (error) {
                    is SSLException -> false
                    is HttpStatusException -> error.statusCode in setOf(408, 500, 502, 503, 504)
                    else -> true
                }
                if (!retryable || attempt == 2) throw error
                pause(1000L shl attempt)
            }
        }
        error("Unreachable")
    }
}

private fun downloadGoldPage(): Document = Jsoup.connect("https://www.profinance.ru/commodities/gold")
    .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
    .timeout(20_000)
    .maxBodySize(2 * 1024 * 1024)
    .get()

internal class GoldPriceFormatException(message: String) : IllegalStateException(message)

internal fun parseGoldPrice(document: Document): Double {
    val row = document.select("tr").firstOrNull { row ->
        row.select("td").firstOrNull()?.text()?.trim() == "XAURUB_FX"
    } ?: throw GoldPriceFormatException("На странице ProFinance нет котировки XAURUB_FX")

    // Prefer the named Last column. Never substitute bid, ask, a percentage or a time.
    val headers = row.closest("table")?.select("thead th").orEmpty()
    val lastColumn = if (headers.isEmpty()) 3 else headers.indexOfFirst {
        it.text().trim().equals("Last", ignoreCase = true)
    }
    val rawPrice = row.select("td").getOrNull(lastColumn)?.text()
        ?: throw GoldPriceFormatException("На странице ProFinance нет столбца Last")
    val normalized = rawPrice.replace(Regex("[\\s\\u00a0\\u202f]+"), "").replace(',', '.')
    val price = normalized.takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) }?.toDoubleOrNull()
    if (price == null || !price.isFinite() || price <= 0) {
        throw GoldPriceFormatException("ProFinance вернул котировку без корректной цены")
    }
    return price
}

internal fun priceErrorMessage(error: Exception): String = when (error) {
    is HttpStatusException -> when (error.statusCode) {
        403 -> "ProFinance отклонил запрос (HTTP 403). Попробуйте другую сеть или повторите позже."
        429 -> "ProFinance ограничил частоту запросов. Подождите перед обновлением."
        in 500..599 -> "ProFinance временно недоступен (HTTP ${error.statusCode}). Повторите позже."
        else -> "ProFinance вернул ошибку HTTP ${error.statusCode}."
    }
    is SocketTimeoutException -> "ProFinance не ответил вовремя. Проверьте соединение и повторите позже."
    is UnknownHostException -> "Не удалось найти сервер ProFinance. Проверьте интернет-соединение."
    is SSLException -> "Не удалось установить защищённое соединение с ProFinance. Проверьте дату и время устройства."
    is GoldPriceFormatException -> "${error.message}. Повторите позже."
    is IOException -> "Не удалось загрузить курс ProFinance. Проверьте соединение и повторите позже."
    else -> "Не удалось обновить курс. Повторите позже."
}
