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
            messages = agent.generate(prompt),
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
        val responseText = httpClient.post("${baseUrl.trimEnd('/')}/responses") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("client", "a2ui-android")
            header("client-version", BuildConfig.VERSION_NAME)
            setBody(
                buildJsonObject {
                    put("model", model)
                    put("input", buildA2UiComposerPrompt(prompt))
                }.toString()
            )
        }.bodyAsText()

        val modelText = OciLiteLlmResponseParser.extractText(responseText)
        val messages = A2UiGenerationParser.parseMessages(modelText)
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

private fun buildA2UiComposerPrompt(prompt: String): String {
    return """
        You are A2UI Composer for a native Android Jetpack Compose renderer.
        Return only JSON, no markdown and no prose.

        Output format:
        {
          "messages": [
            { "version": "v0.9", "deleteSurface": { "surfaceId": "$DynamicSurfaceId" } },
            { "version": "v0.9", "createSurface": { "surfaceId": "$DynamicSurfaceId", "catalogId": "${A2UiMessages.BasicCatalogId}" } },
            { "version": "v0.9", "updateDataModel": { "surfaceId": "$DynamicSurfaceId", "path": "/", "value": {} } },
            { "version": "v0.9", "updateComponents": { "surfaceId": "$DynamicSurfaceId", "components": [] } }
          ]
        }

        Requirements:
        - Use surfaceId "$DynamicSurfaceId".
        - The component tree must include a component with id "root".
        - Use only these components: Column, Row, Card, Text, TextField, Button, CheckBox, List.
        - Use only JSON path bindings like { "path": "/agent/status" } or { "path": "./title" }.
        - Button actions may use { "event": { "name": "select_flight", "context": {} } } or { "event": { "name": "submit_form" } }.
        - Keep the UI compact for phone screens.

        User prompt:
        $prompt

        Received runtime JSON data:
        $ReceivedFlightsJson
    """.trimIndent()
}

private fun prettyA2UiMessages(messages: List<String>): String {
    val messageElements = messages.map { compactJson.parseToJsonElement(it) }
    return prettyJson.encodeToString(JsonElement.serializer(), JsonArray(messageElements))
}
