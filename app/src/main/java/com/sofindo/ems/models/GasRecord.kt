package com.sofindo.ems.models

import java.util.Date

data class GasRecord(
    val date: Date,
    val newGas: String,
    val totalGas: String,
    val totalCost: String,
    val recBy: String
)



