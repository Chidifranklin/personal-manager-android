package com.example.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object CurrencyFormatter {

    private val formatters = ConcurrentHashMap<String, NumberFormat>()

    /**
     * Formats an amount using java.text.NumberFormat.getCurrencyInstance()
     * with the specified ISO currency code.
     */
    fun format(amount: Double, currencyCode: String): String {
        return try {
            val formatter = formatters.getOrPut(currencyCode) {
                val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
                try {
                    format.currency = Currency.getInstance(currencyCode)
                } catch (e: Exception) {
                    format.currency = Currency.getInstance("USD")
                }
                format
            }
            synchronized(formatter) {
                formatter.format(amount)
            }
        } catch (e: Exception) {
            val symbol = getCurrencySymbol(currencyCode)
            String.format(Locale.getDefault(), "%s%.2f", symbol, amount)
        }
    }

    /**
     * Returns the visual symbol for a given currency code.
     */
    fun getCurrencySymbol(currencyCode: String): String {
        return try {
            val currency = Currency.getInstance(currencyCode)
            currency.getSymbol(Locale.getDefault())
        } catch (e: Exception) {
            currencyCode
        }
    }
}
