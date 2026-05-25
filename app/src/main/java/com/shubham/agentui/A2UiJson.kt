package com.shubham.agentui

import kotlinx.serialization.json.Json

internal val compactJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal val prettyJson = Json {
    prettyPrint = true
}
