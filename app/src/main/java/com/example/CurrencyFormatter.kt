package com.example

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val indonesianLocale = Locale.forLanguageTag("id-ID")
    
    // NumberFormat is not thread-safe, but ThreadLocal can help if needed.
    // For this app, simplicity is key.
    fun formatRp(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(indonesianLocale).apply {
            maximumFractionDigits = 0
        }
        return "Rp " + formatter.format(amount)
    }
}
