package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Currency
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "personal_manager_settings")

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: String
)

class PreferenceManager(private val context: Context) {

    companion object {
        val KEY_CURRENCY_CODE = stringPreferencesKey("user_currency_code")

        fun getDefaultCurrencyCode(): String {
            return try {
                Currency.getInstance(Locale.getDefault())?.currencyCode ?: "USD"
            } catch (e: Exception) {
                "USD"
            }
        }

        val SUPPORTED_CURRENCIES = listOf(
            CurrencyInfo("USD", "$", "US Dollar"),
            CurrencyInfo("EUR", "€", "Euro"),
            CurrencyInfo("GBP", "£", "British Pound"),
            CurrencyInfo("NGN", "₦", "Nigerian Naira"),
            CurrencyInfo("CAD", "CA$", "Canadian Dollar"),
            CurrencyInfo("AUD", "AU$", "Australian Dollar"),
            CurrencyInfo("JPY", "¥", "Japanese Yen"),
            CurrencyInfo("INR", "₹", "Indian Rupee"),
            CurrencyInfo("CNY", "CN¥", "Chinese Yuan"),
            CurrencyInfo("BRL", "R$", "Brazilian Real"),
            CurrencyInfo("ZAR", "R", "South African Rand"),
            CurrencyInfo("MXN", "MX$", "Mexican Peso"),
            CurrencyInfo("SGD", "SG$", "Singapore Dollar"),
            CurrencyInfo("CHF", "CHF", "Swiss Franc"),
            CurrencyInfo("AED", "AED", "UAE Dirham"),
            CurrencyInfo("SAR", "SAR", "Saudi Riyal"),
            CurrencyInfo("KES", "KSh", "Kenyan Shilling"),
            CurrencyInfo("GHS", "GH₵", "Ghanaian Cedi"),
            CurrencyInfo("PHP", "₱", "Philippine Peso"),
            CurrencyInfo("KRW", "₩", "South Korean Won"),
            CurrencyInfo("TRY", "₺", "Turkish Lira"),
            CurrencyInfo("NZD", "NZ$", "New Zealand Dollar"),
            CurrencyInfo("SEK", "kr", "Swedish Krona"),
            CurrencyInfo("NOK", "kr", "Norwegian Krone")
        )
    }

    val currencyCodeFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_CURRENCY_CODE] ?: getDefaultCurrencyCode()
        }

    suspend fun setCurrencyCode(currencyCode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CURRENCY_CODE] = currencyCode.uppercase().trim()
        }
    }
}
