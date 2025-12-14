package com.sofindo.ems.models

import java.util.*

data class KwhtmRecord(
    val date: Date,
    val recLWBP: String,
    val recWBP: String,
    val recKVARH: String,
    val consumeLWBP: String,
    val consumeWBP: String,
    val consumeKVARH: String,
    val costLWBP: String,
    val costWBP: String,
    val costKVARH: String,
    val totalCost: String,
    val recBy: String
)

