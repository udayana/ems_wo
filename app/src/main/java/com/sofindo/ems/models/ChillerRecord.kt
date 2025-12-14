package com.sofindo.ems.models

import java.util.Date

data class ChillerRecord(
    override val date: Date,
    override val tempRecord: String,
    val diff: String,
    val upDown: String,
    val recBy: String
) : TemperatureRecord

