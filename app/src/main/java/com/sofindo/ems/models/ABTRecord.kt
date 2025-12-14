package com.sofindo.ems.models

import java.util.Date

data class ABTRecord(
    val date: Date,
    val meterRecord: String,
    val consume: String,
    val estimateCost: String,
    val recBy: String
)

