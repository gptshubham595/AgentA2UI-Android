package com.shubham.agentui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Stable
internal class A2UiRuntime {
    val surfaces = mutableStateMapOf<String, A2UiSurfaceState>()
    val dataModels = mutableStateMapOf<String, JsonElement>()
    var lastMessages by mutableStateOf(emptyList<String>())
    var actionHandler: ((A2UiAction) -> Unit)? = null

    fun processMessages(messages: List<String>) {
        if (messages.isEmpty()) return
        lastMessages = messages
        messages.forEach(::processMessage)
    }

    fun processMessage(message: String) {
        val envelope = compactJson.parseToJsonElement(message).jsonObject
        when {
            "createSurface" in envelope -> {
                val payload = envelope.getValue("createSurface").jsonObject
                val surfaceId = payload.getValue("surfaceId").jsonPrimitive.content
                surfaces[surfaceId] = A2UiSurfaceState(
                    surfaceId = surfaceId,
                    catalogId = payload["catalogId"]?.jsonPrimitive?.contentOrNull ?: "standard"
                )
                dataModels.putIfAbsent(surfaceId, buildJsonObject {})
            }

            "updateComponents" in envelope -> {
                val payload = envelope.getValue("updateComponents").jsonObject
                val surfaceId = payload.getValue("surfaceId").jsonPrimitive.content
                val surface = surfaces.getOrPut(surfaceId) {
                    A2UiSurfaceState(surfaceId = surfaceId, catalogId = "standard")
                }
                payload.getValue("components").jsonArray.forEach { item ->
                    val component = item.jsonObject
                    surface.components[component.getValue("id").jsonPrimitive.content] = component
                }
            }

            "updateDataModel" in envelope -> {
                val payload = envelope.getValue("updateDataModel").jsonObject
                val surfaceId = payload.getValue("surfaceId").jsonPrimitive.content
                val path = payload["path"]?.jsonPrimitive?.content ?: "/"
                if ("value" in payload) {
                    updateDataModel(surfaceId, path, payload.getValue("value"))
                } else {
                    deleteDataModelValue(surfaceId, path)
                }
            }

            "deleteSurface" in envelope -> {
                val payload = envelope.getValue("deleteSurface").jsonObject
                val surfaceId = payload.getValue("surfaceId").jsonPrimitive.content
                surfaces.remove(surfaceId)
                dataModels.remove(surfaceId)
            }
        }
    }

    fun component(surfaceId: String, componentId: String): JsonObject? {
        return surfaces[surfaceId]?.components?.get(componentId)
    }

    fun dataSnapshot(surfaceId: String): JsonObject {
        return dataModels[surfaceId] as? JsonObject ?: buildJsonObject {}
    }

    fun resolveText(surfaceId: String, value: JsonElement?, scopePath: String?): String {
        val resolved = resolveValue(surfaceId, value, scopePath)
        return when (resolved) {
            null, JsonNull -> ""
            is JsonPrimitive -> resolved.contentOrNull ?: resolved.toString()
            else -> resolved.toString()
        }
    }

    fun resolveValue(surfaceId: String, value: JsonElement?, scopePath: String?): JsonElement? {
        return when (value) {
            null -> null
            is JsonObject -> {
                when {
                    "path" in value -> resolvePath(surfaceId, value.getValue("path").jsonPrimitive.content, scopePath)
                    "literal" in value -> value["literal"]
                    else -> value
                }
            }

            else -> value
        }
    }

    fun resolvePath(surfaceId: String, path: String, scopePath: String?): JsonElement? {
        return getAtPath(dataModels[surfaceId] ?: buildJsonObject {}, scopedPath(path, scopePath))
    }

    fun updateBoundValue(surfaceId: String, binding: JsonElement?, scopePath: String?, value: JsonElement) {
        val bindingObject = binding as? JsonObject ?: return
        val path = bindingObject["path"]?.jsonPrimitive?.content ?: return
        updateDataModel(surfaceId, scopedPath(path, scopePath), value)
    }

    fun updateDataModel(surfaceId: String, path: String, value: JsonElement) {
        val root = dataModels[surfaceId] ?: buildJsonObject {}
        dataModels[surfaceId] = setAtPath(root, pathSegments(path), value)
    }

    fun deleteDataModelValue(surfaceId: String, path: String) {
        if (path == "/") {
            dataModels[surfaceId] = buildJsonObject {}
            return
        }

        val root = dataModels[surfaceId] ?: return
        dataModels[surfaceId] = removeAtPath(root, pathSegments(path))
    }

    fun dispatchAction(surfaceId: String, action: JsonObject, scopePath: String?) {
        val event = action["event"]?.jsonObject ?: return
        val name = event["name"]?.jsonPrimitive?.content ?: return
        val context = event["context"]?.jsonObject.orEmpty().mapValues { (_, value) ->
            resolveValue(surfaceId, value, scopePath) ?: JsonNull
        }
        actionHandler?.invoke(A2UiAction(surfaceId, name, context))
    }

    fun scopedPath(path: String, scopePath: String?): String {
        return when {
            path == "." -> scopePath ?: "/"
            path.startsWith("./") -> {
                val base = scopePath ?: "/"
                "${base.trimEnd('/')}/${path.removePrefix("./")}"
            }

            path.startsWith("/") -> path
            scopePath != null -> "${scopePath.trimEnd('/')}/$path"
            else -> "/$path"
        }
    }
}

internal class A2UiSurfaceState(
    val surfaceId: String,
    val catalogId: String
) {
    val components = mutableStateMapOf<String, JsonObject>()
}

internal data class A2UiAction(
    val surfaceId: String,
    val name: String,
    val context: Map<String, JsonElement>
)
