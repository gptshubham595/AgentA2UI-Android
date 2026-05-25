package com.shubham.agentui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun getAtPath(root: JsonElement, path: String): JsonElement? {
    if (path == "/") return root
    return pathSegments(path).fold(root as JsonElement?) { current, segment ->
        when (current) {
            is JsonObject -> current[segment]
            is JsonArray -> segment.toIntOrNull()?.let { current.getOrNull(it) }
            else -> null
        }
    }
}

internal fun setAtPath(current: JsonElement, segments: List<String>, value: JsonElement): JsonElement {
    if (segments.isEmpty()) return value
    val head = segments.first()
    val tail = segments.drop(1)

    return when (current) {
        is JsonObject -> {
            val next = current[head] ?: emptyContainerFor(tail)
            JsonObject(current.toMutableMap().apply {
                put(head, setAtPath(next, tail, value))
            })
        }

        is JsonArray -> {
            val index = head.toIntOrNull() ?: return current
            JsonArray(current.toMutableList().apply {
                while (size <= index) add(emptyContainerFor(tail))
                set(index, setAtPath(get(index), tail, value))
            })
        }

        else -> setAtPath(emptyContainerFor(segments), segments, value)
    }
}

internal fun removeAtPath(current: JsonElement, segments: List<String>): JsonElement {
    if (segments.isEmpty()) return current
    val head = segments.first()
    val tail = segments.drop(1)

    return when (current) {
        is JsonObject -> {
            if (tail.isEmpty()) {
                JsonObject(current.toMutableMap().apply { remove(head) })
            } else {
                val next = current[head] ?: return current
                JsonObject(current.toMutableMap().apply {
                    put(head, removeAtPath(next, tail))
                })
            }
        }

        is JsonArray -> {
            val index = head.toIntOrNull() ?: return current
            if (index !in current.indices) return current

            JsonArray(current.toMutableList().apply {
                if (tail.isEmpty()) {
                    removeAt(index)
                } else {
                    set(index, removeAtPath(get(index), tail))
                }
            })
        }

        else -> current
    }
}

internal fun pathSegments(path: String): List<String> {
    return path.trim().trim('/').split('/').filter { it.isNotBlank() }
}

internal fun JsonObject.string(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}

internal fun JsonObject.pathString(path: String): String {
    return getAtPath(this, path)?.jsonPrimitive?.contentOrNull.orEmpty()
}

internal fun Map<String, JsonElement>.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

internal fun JsonElement.jsonArrayOrNull(): JsonArray? {
    return this as? JsonArray
}

private fun emptyContainerFor(remainingSegments: List<String>): JsonElement {
    return if (remainingSegments.firstOrNull()?.toIntOrNull() != null) {
        JsonArray(emptyList())
    } else {
        buildJsonObject {}
    }
}
