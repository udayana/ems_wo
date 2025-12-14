package com.sofindo.ems.models

import java.util.Date

data class FuelRecord(
    val date: Date,
    val newFuel: String,
    val totalFuel: String,
    val totalCost: String,
    val recBy: String
)
