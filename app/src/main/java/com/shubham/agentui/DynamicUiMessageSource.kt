package com.shubham.agentui

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal data class DynamicUiGeneration(
    val messages: List<String>,
    val sourceLabel: String,
    val rawJson: String = prettyA2UiMessages(messages),
    val warning: String? = null
)

internal const val TemporaryPlaygroundPrompt =
    "Create a compact A2UI playground screen showing that the temporary OCI AI agent can generate native UI JSON."

internal fun normalizedPlaygroundPrompt(prompt: String): String {
    return prompt.trim().ifBlank { TemporaryPlaygroundPrompt }
}

internal interface DynamicUiMessageSource {
    val sourceLabel: String

    suspend fun generate(prompt: String): DynamicUiGeneration
}

internal object DynamicUiMessageSources {
    fun create(): DynamicUiMessageSource {
        val local = LocalDynamicUiMessageSource()
        val shouldUseOci = BuildConfig.A2UI_USE_OCI_AGENT &&
            BuildConfig.A2UI_OCI_OPENAI_API_KEY.isNotBlank()

        if (!shouldUseOci) return local

        return FallbackDynamicUiMessageSource(
            primary = OciDynamicUiMessageSource(
                apiKey = BuildConfig.A2UI_OCI_OPENAI_API_KEY,
                baseUrl = BuildConfig.A2UI_OCI_LITELLM_BASE_URL,
                model = BuildConfig.A2UI_OCI_LITELLM_MODEL
            ),
            fallback = local
        )
    }
}

internal class LocalDynamicUiMessageSource(
    private val agent: DynamicUiAgent = DynamicUiAgent()
) : DynamicUiMessageSource {
    override val sourceLabel = "Local A2UI JSON"

    override suspend fun generate(prompt: String): DynamicUiGeneration {
        return DynamicUiGeneration(
            messages = agent.generate(normalizedPlaygroundPrompt(prompt)),
            sourceLabel = sourceLabel
        )
    }
}

internal class FallbackDynamicUiMessageSource(
    private val primary: DynamicUiMessageSource,
    private val fallback: DynamicUiMessageSource
) : DynamicUiMessageSource {
    override val sourceLabel = primary.sourceLabel

    override suspend fun generate(prompt: String): DynamicUiGeneration {
        return try {
            primary.generate(prompt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            fallback.generate(prompt).copy(
                sourceLabel = "${fallback.sourceLabel} fallback",
                warning = "OCI generation failed: ${error.message.orEmpty().ifBlank { error::class.simpleName }}"
            )
        }
    }
}

internal class OciDynamicUiMessageSource(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    private val httpClient: HttpClient = HttpClient(Android)
) : DynamicUiMessageSource {
    override val sourceLabel = "OCI LiteLLM $model"

    override suspend fun generate(prompt: String): DynamicUiGeneration {
        val requestPrompt = normalizedPlaygroundPrompt(prompt)
        val responseText = httpClient.post("${baseUrl.trimEnd('/')}/responses") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("client", "a2ui-android")
            header("client-version", BuildConfig.VERSION_NAME)
            setBody(
                buildJsonObject {
                    put("model", model)
                    put("input", buildA2UiComposerPrompt(requestPrompt))
                }.toString()
            )
        }.bodyAsText()

        val modelText = OciLiteLlmResponseParser.extractText(responseText)
        val messages = A2UiGenerationParser.parseMessages(modelText)
        A2UiGenerationParser.validateSupportedMessages(messages)
        return DynamicUiGeneration(
            messages = messages,
            sourceLabel = sourceLabel,
            rawJson = prettyA2UiMessages(messages)
        )
    }
}

internal object A2UiGenerationParser {
    private val messageKeys = setOf(
        "createSurface",
        "updateDataModel",
        "updateComponents",
        "deleteSurface"
    )
    private val supportedComponents = setOf(
        "Surface",
        "Box",
        "Circle",
        "Column",
        "Row",
        "Card",
        "Text",
        "TextField",
        "Button",
        "CheckBox",
        "List",
        "Spacer",
        "Icon",
        "Image",
        "Divider",
        "Title",
        "DashboardCard",
        "Metric",
        "Badge",
        "DataTable",
        "PieChart",
        "BarChart",
        "FlightCard"
    )

    fun parseMessages(modelText: String): List<String> {
        val cleaned = stripMarkdownFence(modelText)
        structuredMessages(cleaned).takeIf { it.isNotEmpty() }?.let { return it }
        jsonLineMessages(cleaned).takeIf { it.isNotEmpty() }?.let { return it }

        val jsonSlice = cleaned.likelyJsonSlice()
        if (jsonSlice != cleaned) {
            structuredMessages(jsonSlice).takeIf { it.isNotEmpty() }?.let { return it }
        }

        error("Model response did not contain A2UI messages.")
    }

    fun validateSupportedMessages(messages: List<String>) {
        val envelopes = messages.map { compactJson.parseToJsonElement(it).jsonObject }
        if (envelopes.none { "createSurface" in it }) {
            error("Model response did not include createSurface.")
        }
        if (envelopes.none { "updateDataModel" in it }) {
            error("Model response did not include updateDataModel.")
        }
        if (envelopes.none { "updateComponents" in it }) {
            error("Model response did not include updateComponents.")
        }

        val components = envelopes
            .firstOrNull { "updateComponents" in it }
            ?.get("updateComponents")
            ?.jsonObject
            ?.get("components")
            ?.jsonArrayOrNull()
            ?.flatMap { componentTree(it as? JsonObject) }
            .orEmpty()

        if (components.isEmpty()) {
            error("Model response did not include updateComponents.")
        }
        if (components.none { it.string("id") == "root" }) {
            error("Model response did not include a root component.")
        }

        val unsupported = components
            .mapNotNull { it.string("component") }
            .filterNot { it in supportedComponents }
            .distinct()
        if (unsupported.isNotEmpty()) {
            error("Model used unsupported A2UI components: ${unsupported.joinToString()}.")
        }
    }

    private fun componentTree(component: JsonObject?): List<JsonObject> {
        if (component == null) return emptyList()
        return buildList {
            add(component)
            addAll(inlineChildComponents(component["child"]))
            addAll(inlineChildComponents(component["children"]))
        }
    }

    private fun inlineChildComponents(value: JsonElement?): List<JsonObject> {
        return when (value) {
            is JsonObject -> {
                val direct = if (!value.string("component").isNullOrBlank()) {
                    componentTree(value)
                } else {
                    emptyList()
                }
                direct +
                    inlineChildComponents(value["array"]) +
                    inlineChildComponents(value["component"]) +
                    inlineChildComponents(value["itemComponent"])
            }

            is JsonArray -> value.flatMap { inlineChildComponents(it) }
            else -> emptyList()
        }
    }

    private fun structuredMessages(text: String): List<String> {
        val element = runCatching { compactJson.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        return messagesFromElement(element)
    }

    private fun jsonLineMessages(text: String): List<String> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                runCatching { compactJson.parseToJsonElement(line) }
                    .getOrNull()
                    ?.let(::normalizeMessage)
            }
            .toList()
    }

    private fun messagesFromElement(element: JsonElement): List<String> {
        return when (element) {
            is JsonArray -> element.mapNotNull(::normalizeMessage)
            is JsonObject -> {
                val messages = element["messages"] ?: element["a2uiMessages"]
                if (messages != null) {
                    messagesFromElement(messages)
                } else {
                    listOfNotNull(normalizeMessage(element))
                }
            }

            else -> listOfNotNull(normalizeMessage(element))
        }
    }

    private fun normalizeMessage(element: JsonElement): String? {
        return when (element) {
            is JsonObject -> element.takeIf { obj -> messageKeys.any { it in obj } }?.toString()
            is JsonPrimitive -> {
                val content = element.contentOrNull ?: return null
                runCatching { compactJson.parseToJsonElement(content) }
                    .getOrNull()
                    ?.let(::normalizeMessage)
            }

            else -> null
        }
    }

    private fun stripMarkdownFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed

        return trimmed.lines()
            .drop(1)
            .dropLastWhile { it.trim() == "```" }
            .joinToString("\n")
            .trim()
    }

    private fun String.likelyJsonSlice(): String {
        val start = indexOfFirst { it == '{' || it == '[' }
        val end = maxOf(lastIndexOf('}'), lastIndexOf(']'))
        return if (start >= 0 && end >= start) substring(start, end + 1).trim() else this
    }
}

internal object OciLiteLlmResponseParser {
    fun extractText(responseText: String): String {
        val payloadText = firstJsonPayload(responseText)
        val payload = compactJson.parseToJsonElement(payloadText).jsonObject

        payload.string("output_text")?.takeIf { it.isNotBlank() }?.let { return it }

        payload["output"]?.jsonArrayOrNull().orEmpty().forEach { item ->
            val content = (item as? JsonObject)
                ?.get("content")
                ?.jsonArrayOrNull()
                .orEmpty()
            content.forEach { part ->
                val partObject = part as? JsonObject ?: return@forEach
                val type = partObject.string("type")
                val text = partObject.string("text")
                if (type == "output_text" && !text.isNullOrBlank()) return text
            }
        }

        payload["choices"]?.jsonArrayOrNull().orEmpty().forEach { choice ->
            val text = (choice as? JsonObject)
                ?.get("message")
                ?.jsonObject
                ?.string("content")
            if (!text.isNullOrBlank()) return text
        }

        error("No text output found in OCI LiteLLM response.")
    }

    private fun firstJsonPayload(responseText: String): String {
        val trimmed = responseText.trim()
        if (trimmed.startsWith("{")) return trimmed

        responseText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("data:")) {
                val data = line.removePrefix("data:").trim()
                if (data.isNotBlank() && data != "[DONE]") return data
            }
        }

        return trimmed
    }
}

internal fun buildA2UiComposerPrompt(prompt: String): String {
    val requestPrompt = normalizedPlaygroundPrompt(prompt)
    return """
        You are A2UI Composer for a native Android Jetpack Compose renderer. Return only JSON.
        Do not return markdown, comments, prose, JSONL, escaped JSON strings, or keys outside the contract.

        REQUIRED TOP-LEVEL SHAPE:
        {
          "messages": [
            { "version": "v0.9", "deleteSurface": { "surfaceId": "$DynamicSurfaceId" } },
            { "version": "v0.9", "createSurface": { "surfaceId": "$DynamicSurfaceId", "catalogId": "${A2UiMessages.BasicCatalogId}" } },
            { "version": "v0.9", "updateDataModel": { "surfaceId": "$DynamicSurfaceId", "path": "/", "value": {} } },
            { "version": "v0.9", "updateComponents": { "surfaceId": "$DynamicSurfaceId", "components": [] } }
          ]
        }

        STRICT RULES:
        - The messages array must include createSurface, updateDataModel, and updateComponents.
        - Use exactly surfaceId "$DynamicSurfaceId".
        - updateDataModel must use path "/" and must contain the data the UI binds to.
        - updateComponents must contain a flat component table. Every component needs a stable string id.
        - Include exactly one root component with id "root". Prefer root component "Column".
        - Do not nest component objects inside child or children. Use ids and template children instead.
        - Use only path bindings: { "path": "/screen/title" }, { "path": "./name" }, etc.
        - Keep the UI compact for phone screens; prefer vertical Column layouts and cards.
        - For requests about plain colored shapes, backgrounds, rectangles, circles, center/mid placement, or drawing-like layouts, use Box and Circle. Do not approximate shapes with Badge, Metric, or text-only cards.

        ALLOWED ANDROID CATALOG:
        - Box: { "id", "component": "Box", "child"?, "children"?, "backgroundColor"?, "containerColor"?, "contentColor"?, "textColor"?, "widthDp"?, "heightDp"?, "minHeightDp"?, "paddingDp"?, "cornerRadiusDp"?, "contentAlignment"?, "align"?, "weight"? }
        - Circle: { "id", "component": "Circle", "text"?, "child"?, "color"?, "backgroundColor"?, "contentColor"?, "textColor"?, "sizeDp"?, "paddingDp"?, "weight"? }
        - Column: { "id", "component": "Column", "children", "spacingDp"?, "align"? }
        - Row: { "id", "component": "Row", "children", "spacingDp"?, "align"?, "weight"? }
        - Card: { "id", "component": "Card", "child"?, "children"?, "paddingDp"?, "containerColor"?, "weight"? }
        - Text: { "id", "component": "Text", "text", "variant"?, "maxLines"?, "color"?, "textColor"?, "weight"? }
        - TextField: { "id", "component": "TextField", "label", "value", "placeholder"?, "variant"? }
        - Button: { "id", "component": "Button", "child"?, "text"?, "variant"?, "action"? }
        - CheckBox: { "id", "component": "CheckBox", "label"?, "value", "action"? }
        - List: { "id", "component": "List", "children": { "path", "componentId" } }
        - Spacer: { "id", "component": "Spacer", "heightDp"? }
        - Divider: { "id", "component": "Divider" }
        - Icon: { "id", "component": "Icon", "name", "sizeDp"? }
        - Image: { "id", "component": "Image", "url", "fit"?, "variant"?, "sizeDp"?, "contentDescription"? }
        - Title: { "id", "component": "Title", "text", "level"? }
        - DashboardCard: { "id", "component": "DashboardCard", "title", "subtitle"?, "child"? }
        - Metric: { "id", "component": "Metric", "label", "value", "trend"?, "trendValue"?, "weight"? }
        - Badge: { "id", "component": "Badge", "text", "variant"? }
        - DataTable: { "id", "component": "DataTable", "columns", "rows" }
        - PieChart: { "id", "component": "PieChart", "data", "innerRadius"? }
        - BarChart: { "id", "component": "BarChart", "data", "color"?, "valuePrefix"?, "valueSuffix"? }
        - FlightCard: { "id", "component": "FlightCard", "airline", "airlineLogo", "flightNumber", "origin", "destination", "date", "departureTime", "arrivalTime", "duration", "status", "price", "action"? }

        CHILDREN CONTRACT:
        - Static children: "children": ["title", "summary_card", "list"]
        - Single child slot: "child": "card_body"
        - Repeated template children: "children": { "path": "/players", "componentId": "player_card" }
        - Inside a repeated component, bind item fields with relative paths like { "path": "./name" }.
        - For repeated rows, relative paths may use either "./field" or "field"; both resolve against the current item.
        - For date display, use { "call": "formatDate", "args": { "value": { "path": "timestamp" }, "format": "h:mm a" }, "returnType": "string" }.
        - Use Image only for small meaningful media like avatars/logos. Use Icon only for simple labels like info/status.
        - For dashboards, prefer DashboardCard, Metric, BarChart, PieChart, Badge, and DataTable over inventing new components.
        - For colors, use literal hex strings like "#D32F2F" or dynamic path bindings to hex strings in the data model.

        GOOD EXAMPLE FOR A COLOR SHAPE LAYOUT:
        {
          "messages": [
            { "version": "v0.9", "deleteSurface": { "surfaceId": "$DynamicSurfaceId" } },
            { "version": "v0.9", "createSurface": { "surfaceId": "$DynamicSurfaceId", "catalogId": "${A2UiMessages.BasicCatalogId}" } },
            {
              "version": "v0.9",
              "updateDataModel": {
                "surfaceId": "$DynamicSurfaceId",
                "path": "/",
                "value": {
                  "art": {
                    "backgroundColor": "#D32F2F",
                    "rectangleColor": "#FFFFFF",
                    "circleColor": "#FFD54F",
                    "textColor": "#111111",
                    "label": "HI"
                  }
                }
              }
            },
            {
              "version": "v0.9",
              "updateComponents": {
                "surfaceId": "$DynamicSurfaceId",
                "components": [
                  { "id": "root", "component": "Box", "backgroundColor": { "path": "/art/backgroundColor" }, "heightDp": 360, "paddingDp": 24, "contentAlignment": "center", "child": "white_rectangle" },
                  { "id": "white_rectangle", "component": "Box", "backgroundColor": { "path": "/art/rectangleColor" }, "heightDp": 180, "cornerRadiusDp": 0, "contentAlignment": "center", "child": "yellow_circle" },
                  { "id": "yellow_circle", "component": "Circle", "color": { "path": "/art/circleColor" }, "textColor": { "path": "/art/textColor" }, "text": { "path": "/art/label" }, "sizeDp": 96 }
                ]
              }
            }
          ]
        }

        GOOD EXAMPLE FOR A LEADERBOARD:
        {
          "messages": [
            { "version": "v0.9", "deleteSurface": { "surfaceId": "$DynamicSurfaceId" } },
            { "version": "v0.9", "createSurface": { "surfaceId": "$DynamicSurfaceId", "catalogId": "${A2UiMessages.BasicCatalogId}" } },
            {
              "version": "v0.9",
              "updateDataModel": {
                "surfaceId": "$DynamicSurfaceId",
                "path": "/",
                "value": {
                  "screen": { "title": "Top Football Players", "subtitle": "Season scoring leaderboard" },
                  "players": [
                    { "rank": "1", "name": "Kylian Mbappe", "team": "Real Madrid", "score": "28" },
                    { "rank": "2", "name": "Erling Haaland", "team": "Manchester City", "score": "26" }
                  ],
                  "agent": { "status": "Generated from prompt." }
                }
              }
            },
            {
              "version": "v0.9",
              "updateComponents": {
                "surfaceId": "$DynamicSurfaceId",
                "components": [
                  { "id": "root", "component": "Column", "children": ["header_card", "player_list", "agent_status"], "spacingDp": 12 },
                  { "id": "header_card", "component": "Card", "child": "header_column" },
                  { "id": "header_column", "component": "Column", "children": ["screen_title", "screen_subtitle"] },
                  { "id": "screen_title", "component": "Text", "text": { "path": "/screen/title" }, "variant": "h1" },
                  { "id": "screen_subtitle", "component": "Text", "text": { "path": "/screen/subtitle" }, "variant": "caption" },
                  { "id": "player_list", "component": "List", "children": { "path": "/players", "componentId": "player_card" } },
                  { "id": "player_card", "component": "Card", "child": "player_row" },
                  { "id": "player_row", "component": "Row", "children": ["player_rank", "player_name", "player_score"], "align": "center" },
                  { "id": "player_rank", "component": "Text", "text": { "path": "./rank" }, "variant": "h2" },
                  { "id": "player_name", "component": "Text", "text": { "path": "./name" }, "weight": 1 },
                  { "id": "player_score", "component": "Text", "text": { "path": "./score" }, "variant": "h2" },
                  { "id": "agent_status", "component": "Text", "text": { "path": "/agent/status" }, "variant": "caption" }
                ]
              }
            }
          ]
        }

        FLIGHTS DATA RULE:
        If and only if the user asks for flights/travel, use the received runtime JSON below as the data model.
        For every other UI request, create domain-appropriate sample data in updateDataModel.

        User prompt:
        $requestPrompt

        Received runtime JSON data, available only when relevant:
        $ReceivedFlightsJson
    """.trimIndent()
}

private fun prettyA2UiMessages(messages: List<String>): String {
    val messageElements = messages.map { compactJson.parseToJsonElement(it) }
    return prettyJson.encodeToString(JsonElement.serializer(), JsonArray(messageElements))
}
