package com.shubham.agentui

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicUiMessageSourceTest {
    @Test
    fun parserReadsMessagesFromModelJsonObject() {
        val messages = A2UiGenerationParser.parseMessages(
            buildJsonObject {
                put(
                    "messages",
                    JsonArray(
                        listOf(
                            compactJson.parseToJsonElement(A2UiMessages.createSurface(DynamicSurfaceId))
                        )
                    )
                )
            }.toString()
        )

        val payload = compactJson.parseToJsonElement(messages.single()).jsonObject
        assertEquals("v0.9", payload.getValue("version").jsonPrimitive.content)
        assertTrue("createSurface" in payload)
    }

    @Test
    fun parserReadsMessagesFromFencedModelJson() {
        val modelText = """
            ```json
            {
              "messages": [
                ${A2UiMessages.deleteSurface(DynamicSurfaceId)}
              ]
            }
            ```
        """.trimIndent()

        val messages = A2UiGenerationParser.parseMessages(modelText)

        assertEquals(1, messages.size)
        assertTrue("deleteSurface" in compactJson.parseToJsonElement(messages.single()).jsonObject)
    }

    @Test
    fun ociSourceParsesOutputTextIntoA2UiMessages() = runTest {
        val modelText = buildJsonObject {
            put(
                "messages",
                JsonArray(
                    listOf(
                        compactJson.parseToJsonElement(A2UiMessages.createSurface(DynamicSurfaceId))
                    )
                )
            )
        }.toString()
        val responseText = buildJsonObject {
            put("output_text", modelText)
        }.toString()
        val httpClient = HttpClient(
            MockEngine {
                respond(
                    content = responseText,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        val source = OciDynamicUiMessageSource(
            apiKey = "test-key",
            baseUrl = "https://example.test/litellm",
            model = "gpt-test",
            httpClient = httpClient
        )

        val generated = source.generate("make a compact card")

        assertEquals("OCI LiteLLM gpt-test", generated.sourceLabel)
        assertEquals(1, generated.messages.size)
        assertTrue("createSurface" in compactJson.parseToJsonElement(generated.messages.single()).jsonObject)
    }

    @Test
    fun ociResponseParserReadsServerSentEventPayload() {
        val modelText = buildJsonObject {
            put(
                "messages",
                JsonArray(
                    listOf(
                        compactJson.parseToJsonElement(A2UiMessages.deleteSurface(DynamicSurfaceId))
                    )
                )
            )
        }.toString()
        val responseText = """
            data: {"choices":[{"message":{"content":${JsonPrimitive(modelText)}}}]}
            data: [DONE]
        """.trimIndent()

        assertEquals(modelText, OciLiteLlmResponseParser.extractText(responseText))
    }

    @Test
    fun fallbackSourceUsesLocalGeneratorWhenOciFails() = runTest {
        val source = FallbackDynamicUiMessageSource(
            primary = object : DynamicUiMessageSource {
                override val sourceLabel = "broken remote"
                override suspend fun generate(prompt: String): DynamicUiGeneration {
                    error("network down")
                }
            },
            fallback = LocalDynamicUiMessageSource()
        )

        val generated = source.generate("show flights")
        val runtime = A2UiRuntime()
        runtime.processMessages(generated.messages)

        assertTrue(generated.sourceLabel.contains("fallback"))
        assertTrue(generated.warning!!.contains("network down"))
        assertEquals(3, runtime.dataSnapshot(DynamicSurfaceId).getValue("flights").let { it as JsonArray }.size)
    }
}
