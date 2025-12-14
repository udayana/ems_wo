package com.sofindo.ems.models

import java.util.Date

interface TemperatureRecord {
    val date: Date
    val tempRecord: String
}
