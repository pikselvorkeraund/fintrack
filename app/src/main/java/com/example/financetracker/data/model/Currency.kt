package com.example.financetracker.data.model
enum class Currency(val code: String, val symbol: String, val displayName: String) {
    RUB("RUB", "\u20BD", "Russian Ruble"),
    USD("USD", "$", "US Dollar"),
    CNY("CNY", "\u00A5", "Chinese Yuan"),
    THB("THB", "\u0E3F", "Thai Baht"),
    PHP("PHP", "\u20B1", "Philippine Peso");
    companion object {
        fun fromCode(code: String): Currency = entries.find { it.code == code } ?: RUB
    }
}