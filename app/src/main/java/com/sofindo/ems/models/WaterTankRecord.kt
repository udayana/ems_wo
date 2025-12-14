package com.sofindo.ems.models

import java.util.*

data class WaterTankRecord(
    val date: Date,
    val lastMonthRest: String = "",
    val newWater: String = "",
    val totWater: String = "",
    val price: String = "",
    val priceTotal: String = "",
    val recBy: String = ""
)

