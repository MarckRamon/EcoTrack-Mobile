package com.example.ecotrack.ui.pickup

import com.example.ecotrack.ui.pickup.model.PickupOrder
import com.example.ecotrack.models.quote.QuoteResponse

object TempOrderHolder {
    val orders = mutableMapOf<String, PickupOrder>()
    val quotes = mutableMapOf<String, QuoteResponse>()

    fun saveOrder(order: PickupOrder) {
        orders[order.id] = order
    }
    
    fun updateOrder(order: PickupOrder) {
        if (orders.containsKey(order.id)) {
            orders[order.id] = order
        }
    }

    fun getOrder(orderId: String): PickupOrder? {
        return orders[orderId]
    }

    fun removeOrder(orderId: String) {
        orders.remove(orderId)
        quotes.remove(orderId) // Also remove associated quote
    }
    
    fun saveQuote(orderId: String, quote: QuoteResponse) {
        quotes[orderId] = quote
    }
    
    fun getQuote(orderId: String): QuoteResponse? {
        return quotes[orderId]
    }
}
