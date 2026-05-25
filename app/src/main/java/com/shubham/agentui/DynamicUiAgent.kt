package com.shubham.agentui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal const val DynamicSurfaceId = "dynamic_surface"

internal class DynamicUiAgent(
    private val receivedFlightsJson: String = ReceivedFlightsJson
) {
    fun generate(prompt: String): List<String> {
        val isTemporary = prompt.isBlank()
        val request = prompt.ifBlank { "Temporary UI" }
        val spec = when {
            request.looksLikeShapeRequest() -> shapeSpec(request)
            request.contains("flight", ignoreCase = true) -> flightSpec(request)
            request.contains("form", ignoreCase = true) || request.contains("input", ignoreCase = true) -> formSpec(request)
            else -> cardSpec(request, isTemporary)
        }

        return listOf(
            A2UiMessages.deleteSurface(DynamicSurfaceId),
            A2UiMessages.createSurface(
                surfaceId = DynamicSurfaceId,
                agentDisplayName = "Dynamic UI Agent",
                primaryColor = "#1D4ED8"
            ),
            A2UiMessages.updateDataModel(DynamicSurfaceId, "/", spec.data),
            A2UiMessages.updateComponents(DynamicSurfaceId, spec.components)
        )
    }

    fun handle(action: A2UiAction, data: JsonObject): List<String> {
        return when (action.name) {
            "select_flight" -> handleFlightSelection(action)
            "submit_form" -> handleFormSubmission(data)
            else -> emptyList()
        }
    }

    private fun flightSpec(prompt: String): UiSpec {
        val flightsData = compactJson.parseToJsonElement(receivedFlightsJson).jsonObject
        val flights = flightsData["flights"]?.jsonArray.orEmpty()
        val first = flights.firstOrNull()?.jsonObject
        val data = JsonObject(flightsData.toMutableMap().apply {
            put("prompt", JsonPrimitive(prompt))
            put(
                "agent",
                buildJsonObject {
                    put("name", "Dynamic UI Agent")
                    put("status", "Generated ${flights.size} flight cards from received JSON.")
                    put("summary", "${first?.string("origin").orEmpty()} to ${first?.string("destination").orEmpty()}")
                }
            )
        })

        return UiSpec(data = data, components = flightComponents())
    }

    private fun formSpec(prompt: String): UiSpec {
        val data = buildJsonObject {
            put("prompt", prompt)
            putJsonObject("form") {
                put("name", "")
                put("email", "")
                put("notes", "")
            }
            putJsonObject("agent") {
                put("name", "Dynamic UI Agent")
                put("status", "Generated an editable native form from your request.")
                put("summary", "Custom form")
            }
        }

        return UiSpec(data = data, components = formComponents())
    }

    private fun shapeSpec(prompt: String): UiSpec {
        val data = buildJsonObject {
            put("prompt", prompt)
            putJsonObject("art") {
                put("backgroundColor", "#D32F2F")
                put("rectangleColor", "#FFFFFF")
                put("circleColor", "#FFD54F")
                put("textColor", "#111111")
                put("label", "HI")
            }
            putJsonObject("agent") {
                put("name", "Dynamic UI Agent")
                put("status", "Generated a native color-and-shape layout from your request.")
                put("summary", "Shape layout")
            }
        }

        return UiSpec(data = data, components = shapeComponents())
    }

    private fun cardSpec(prompt: String, isTemporary: Boolean): UiSpec {
        val data = buildJsonObject {
            put("prompt", prompt)
            putJsonObject("agent") {
                put("name", "Dynamic UI Agent")
                put(
                    "status",
                    if (isTemporary) {
                        "Generated a temporary native UI from empty playground input."
                    } else {
                        "Generated a native summary card from your prompt."
                    }
                )
                put("summary", if (isTemporary) "Temporary UI" else "Prompt preview")
            }
        }

        return UiSpec(data = data, components = cardComponents())
    }

    private fun handleFlightSelection(action: A2UiAction): List<String> {
        val id = action.context.string("id")
        val flightNumber = action.context.string("flightNumber")
        val airline = action.context.string("airline")
        val price = action.context.string("price")
        val status = if (id.isBlank()) {
            "Could not select that flight."
        } else {
            "Selected $flightNumber with $airline for $price."
        }

        return listOf(
            A2UiMessages.updateDataModel(DynamicSurfaceId, "/selectedFlightId", JsonPrimitive(id)),
            A2UiMessages.updateDataModel(DynamicSurfaceId, "/agent/status", JsonPrimitive(status))
        )
    }

    private fun handleFormSubmission(data: JsonObject): List<String> {
        val name = data.pathString("/form/name").ifBlank { "the user" }
        return listOf(
            A2UiMessages.updateDataModel(
                DynamicSurfaceId,
                "/agent/status",
                JsonPrimitive("Submitted form for $name.")
            )
        )
    }
}

private data class UiSpec(
    val data: JsonObject,
    val components: List<JsonObject>
)

private fun String.looksLikeShapeRequest(): Boolean {
    val hasBackground = contains("background", ignoreCase = true)
    val hasRectangle = contains("rectangle", ignoreCase = true)
    val hasCircle = contains("circle", ignoreCase = true)
    val hasColor = contains("color", ignoreCase = true) || contains("colour", ignoreCase = true)
    return hasCircle && hasRectangle || hasBackground && hasColor
}

private fun shapeComponents(): List<JsonObject> {
    return listOf(
        component(
            "root",
            "Box",
            "backgroundColor" to path("/art/backgroundColor"),
            "heightDp" to JsonPrimitive(360),
            "paddingDp" to JsonPrimitive(24),
            "contentAlignment" to JsonPrimitive("center"),
            "child" to JsonPrimitive("white_rectangle")
        ),
        component(
            "white_rectangle",
            "Box",
            "backgroundColor" to path("/art/rectangleColor"),
            "heightDp" to JsonPrimitive(180),
            "cornerRadiusDp" to JsonPrimitive(0),
            "contentAlignment" to JsonPrimitive("center"),
            "child" to JsonPrimitive("yellow_circle")
        ),
        component(
            "yellow_circle",
            "Circle",
            "color" to path("/art/circleColor"),
            "textColor" to path("/art/textColor"),
            "text" to path("/art/label"),
            "sizeDp" to JsonPrimitive(96)
        )
    )
}

private fun flightComponents(): List<JsonObject> {
    return listOf(
        component("root", "Column", "children" to children("hero_card", "flight_list", "agent_status")),
        component("hero_card", "Card", "child" to JsonPrimitive("hero_column")),
        component("hero_column", "Column", "children" to children("title", "subtitle", "route_summary")),
        component("title", "Text", "text" to JsonPrimitive("Flight options"), "variant" to JsonPrimitive("h1")),
        component("subtitle", "Text", "text" to JsonPrimitive("Native Android UI generated from received JSON."), "variant" to JsonPrimitive("body")),
        component("route_summary", "Text", "text" to path("/agent/summary"), "variant" to JsonPrimitive("h2")),
        component("flight_list", "List", "children" to buildJsonObject {
            put("path", "/flights")
            put("componentId", "flight_card")
        }),
        component("flight_card", "Card", "child" to JsonPrimitive("flight_column")),
        component("flight_column", "Column", "children" to children("flight_header", "route_row", "time_row", "details_row", "select_button")),
        component("flight_header", "Row", "align" to JsonPrimitive("center"), "children" to children("airline_name", "flight_number")),
        component("airline_name", "Text", "text" to path("./airline"), "variant" to JsonPrimitive("h2"), "weight" to JsonPrimitive(1)),
        component("flight_number", "Text", "text" to path("./flightNumber"), "variant" to JsonPrimitive("caption")),
        component("route_row", "Row", "align" to JsonPrimitive("center"), "children" to children("origin", "to_label", "destination")),
        component("origin", "Text", "text" to path("./origin"), "variant" to JsonPrimitive("h2")),
        component("to_label", "Text", "text" to JsonPrimitive("to"), "variant" to JsonPrimitive("caption")),
        component("destination", "Text", "text" to path("./destination"), "variant" to JsonPrimitive("h2")),
        component("time_row", "Row", "align" to JsonPrimitive("center"), "children" to children("departure_time", "duration", "arrival_time")),
        component("departure_time", "Text", "text" to path("./departureTime"), "weight" to JsonPrimitive(1)),
        component("duration", "Text", "text" to path("./duration"), "variant" to JsonPrimitive("caption"), "weight" to JsonPrimitive(1)),
        component("arrival_time", "Text", "text" to path("./arrivalTime"), "weight" to JsonPrimitive(1)),
        component("details_row", "Row", "align" to JsonPrimitive("center"), "children" to children("flight_date", "flight_status", "flight_price")),
        component("flight_date", "Text", "text" to path("./date"), "variant" to JsonPrimitive("caption"), "weight" to JsonPrimitive(1)),
        component("flight_status", "Text", "text" to path("./status"), "variant" to JsonPrimitive("caption"), "weight" to JsonPrimitive(1)),
        component("flight_price", "Text", "text" to path("./price"), "variant" to JsonPrimitive("h2")),
        component("select_label", "Text", "text" to JsonPrimitive("Select flight")),
        component("select_button", "Button", "child" to JsonPrimitive("select_label"), "variant" to JsonPrimitive("primary"), "action" to event(
            "select_flight",
            "id" to path("./id"),
            "flightNumber" to path("./flightNumber"),
            "airline" to path("./airline"),
            "price" to path("./price")
        )),
        component("agent_status", "Text", "text" to path("/agent/status"), "variant" to JsonPrimitive("caption"))
    )
}

private fun formComponents(): List<JsonObject> {
    return listOf(
        component("root", "Column", "children" to children("form_card", "agent_status")),
        component("form_card", "Card", "child" to JsonPrimitive("form_column")),
        component("form_column", "Column", "children" to children("form_title", "name_field", "email_field", "notes_field", "submit_button")),
        component("form_title", "Text", "text" to JsonPrimitive("Generated form"), "variant" to JsonPrimitive("h1")),
        component("name_field", "TextField", "label" to JsonPrimitive("Name"), "value" to path("/form/name"), "variant" to JsonPrimitive("shortText")),
        component("email_field", "TextField", "label" to JsonPrimitive("Email"), "value" to path("/form/email"), "variant" to JsonPrimitive("shortText")),
        component("notes_field", "TextField", "label" to JsonPrimitive("Notes"), "value" to path("/form/notes"), "variant" to JsonPrimitive("longText")),
        component("submit_label", "Text", "text" to JsonPrimitive("Submit form")),
        component("submit_button", "Button", "child" to JsonPrimitive("submit_label"), "variant" to JsonPrimitive("primary"), "action" to event("submit_form")),
        component("agent_status", "Text", "text" to path("/agent/status"), "variant" to JsonPrimitive("caption"))
    )
}

private fun cardComponents(): List<JsonObject> {
    return listOf(
        component("root", "Column", "children" to children("summary_card")),
        component("summary_card", "Card", "child" to JsonPrimitive("summary_column")),
        component("summary_column", "Column", "children" to children("summary_title", "prompt_text", "agent_status")),
        component("summary_title", "Text", "text" to path("/agent/summary"), "variant" to JsonPrimitive("h1")),
        component("prompt_text", "Text", "text" to path("/prompt"), "variant" to JsonPrimitive("body")),
        component("agent_status", "Text", "text" to path("/agent/status"), "variant" to JsonPrimitive("caption"))
    )
}

private fun component(id: String, component: String, vararg attrs: Pair<String, JsonElement>): JsonObject {
    return buildJsonObject {
        put("id", id)
        put("component", component)
        attrs.forEach { (key, value) -> put(key, value) }
    }
}

private fun children(vararg ids: String): JsonArray {
    return buildJsonArray {
        ids.forEach { add(JsonPrimitive(it)) }
    }
}

private fun path(value: String): JsonObject {
    return buildJsonObject {
        put("path", value)
    }
}

private fun event(name: String, vararg context: Pair<String, JsonElement>): JsonObject {
    return buildJsonObject {
        putJsonObject("event") {
            put("name", name)
            if (context.isNotEmpty()) {
                putJsonObject("context") {
                    context.forEach { (key, value) -> put(key, value) }
                }
            }
        }
    }
}
