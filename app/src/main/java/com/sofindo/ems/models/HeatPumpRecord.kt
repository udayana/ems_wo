package com.sofindo.ems.models

import java.util.Date

data class HeatPumpRecord(
    val date: Date,
    var waterIn: String = "",      // °C
    var waterOut: String = "",     // °C
    var highPress: String = "",
    var lowPress: String = "",
    var amp: String = "",
    var volt: String = "",
    val recBy: String = ""
)

