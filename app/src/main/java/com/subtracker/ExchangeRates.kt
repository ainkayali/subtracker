package com.subtracker

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

data class ExchangeRates(
    val ratesToTry: Map<String, Double> = mapOf("TRY" to 1.0),
    val sourceDate: String = "",
    val source: String = "TCMB"
)

class ExchangeRateRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("exchange_rates", Context.MODE_PRIVATE)

    suspend fun load(): ExchangeRates = withContext(Dispatchers.IO) {
        runCatching {
            val latest = fetchFromTcmb()
            save(latest)
            latest
        }.getOrElse {
            loadCached() ?: ExchangeRates()
        }
    }

    private fun fetchFromTcmb(): ExchangeRates {
        val connection = (URL(TcmbTodayXml).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }

        connection.inputStream.use { input ->
            val parser = Xml.newPullParser().apply {
                setInput(input, "UTF-8")
            }
            val rates = mutableMapOf("TRY" to 1.0)
            var sourceDate = ""
            var currentCode: String? = null
            var currentTag: String? = null

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (parser.name == "Tarih_Date") {
                            sourceDate = parser.getAttributeValue(null, "Tarih").orEmpty()
                        }
                        if (parser.name == "Currency") {
                            currentCode = parser.getAttributeValue(null, "Kod")
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (currentTag == "ForexSelling") {
                            val code = currentCode
                            val value = parser.text.trim().replace(',', '.').toDoubleOrNull()
                            if (code != null && value != null) rates[code.uppercase()] = value
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Currency") currentCode = null
                        currentTag = null
                    }
                }
            }

            return ExchangeRates(ratesToTry = rates, sourceDate = sourceDate, source = "TCMB satış")
        }
    }

    private fun save(rates: ExchangeRates) {
        prefs.edit()
            .putString("sourceDate", rates.sourceDate)
            .putString("rates", rates.ratesToTry.entries.joinToString(";") { "${it.key}:${it.value}" })
            .apply()
    }

    private fun loadCached(): ExchangeRates? {
        val raw = prefs.getString("rates", null) ?: return null
        val rates = raw.split(';')
            .mapNotNull {
                val parts = it.split(':')
                if (parts.size != 2) return@mapNotNull null
                parts[0] to (parts[1].toDoubleOrNull() ?: return@mapNotNull null)
            }
            .toMap()
        return ExchangeRates(
            ratesToTry = rates,
            sourceDate = prefs.getString("sourceDate", "").orEmpty(),
            source = "TCMB satış"
        )
    }

    private companion object {
        const val TcmbTodayXml = "https://www.tcmb.gov.tr/kurlar/today.xml"
    }
}
