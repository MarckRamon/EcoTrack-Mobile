package com.example.grabtrash.ui.pickup.model

enum class PaymentMethod {
    GCASH,
    CASH_ON_HAND,
    CREDIT_CARD,
    PAYMAYA,
    GRABPAY,
    BANK_TRANSFER,
    OTC,
    XENDIT_PAYMENT_GATEWAY;
    
    fun getDisplayName(): String {
        return when (this) {
            GCASH -> "GCash"
            CASH_ON_HAND -> "Cash on Hand"
            CREDIT_CARD -> "Credit Card"
            PAYMAYA -> "PayMaya"
            GRABPAY -> "GrabPay"
            BANK_TRANSFER -> "Bank Transfer"
            OTC -> "Over the Counter"
            XENDIT_PAYMENT_GATEWAY -> "XENDIT PAYMENT GATEWAY"
        }
    }
} 