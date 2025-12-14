package com.sofindo.ems.models

import java.util.UUID

data class MaterialItem(
    val id: String = UUID.randomUUID().toString(),
    var materialName: String = "",
    var quantity: String = "",
    var unitPrice: String = "",
    var unit: String = "pcs"
) {
    val amount: Double
        get() {
            val qty = quantity.toDoubleOrNull() ?: 0.0
            val price = unitPrice.toDoubleOrNull() ?: 0.0
            return qty * price
        }
}

