package com.sofindo.ems.models

import java.util.UUID

data class JobItem(
    val id: String = UUID.randomUUID().toString(),
    var description: String = ""
)

