package com.example.ecotrack.ui.pickup

import com.example.ecotrack.ui.pickup.model.PickupOrder

object TempOrderHolder {
    val orders = mutableMapOf<String, PickupOrder>()

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
    }
} 