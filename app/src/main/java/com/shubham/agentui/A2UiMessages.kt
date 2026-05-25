package com.shubham.agentui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal object A2UiMessages {
    private const val ProtocolVersion = "v0.9"
    const val BasicCatalogId = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"

    fun createSurface(
        surfaceId: String,
        agentDisplayName: String = "A2UI Agent",
        primaryColor: String = "#256D6B"
    ): String {
        return buildJsonObject {
            put("version", ProtocolVersion)
            putJsonObject("createSurface") {
                put("surfaceId", surfaceId)
                put("catalogId", BasicCatalogId)
                putJsonObject("theme") {
                    put("primaryColor", primaryColor)
                    put("agentDisplayName", agentDisplayName)
                }
            }
        }.toString()
    }

    fun updateDataModel(surfaceId: String, path: String, value: JsonElement): String {
        return buildJsonObject {
            put("version", ProtocolVersion)
            putJsonObject("updateDataModel") {
                put("surfaceId", surfaceId)
                put("path", path)
                put("value", value)
            }
        }.toString()
    }

    fun updateComponents(surfaceId: String, components: List<JsonObject>): String {
        return buildJsonObject {
            put("version", ProtocolVersion)
            putJsonObject("updateComponents") {
                put("surfaceId", surfaceId)
                put("components", JsonArray(components))
            }
        }.toString()
    }

    fun deleteSurface(surfaceId: String): String {
        return buildJsonObject {
            put("version", ProtocolVersion)
            putJsonObject("deleteSurface") {
                put("surfaceId", surfaceId)
            }
        }.toString()
    }
}
