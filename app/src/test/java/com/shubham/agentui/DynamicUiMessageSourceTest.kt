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
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicUiMessageSourceTest {
    @Test
    fun blankPromptUsesTemporaryOciAgentPrompt() {
        assertEquals(TemporaryPlaygroundPrompt, normalizedPlaygroundPrompt("  "))

        val composerPrompt = buildA2UiComposerPrompt("")

        assertTrue(composerPrompt.contains(TemporaryPlaygroundPrompt))
        assertTrue(composerPrompt.contains("REQUIRED TOP-LEVEL SHAPE"))
    }

    @Test
    fun composerPromptSupportsArbitraryUiRequests() {
        val composerPrompt = buildA2UiComposerPrompt("create a leaderboard for football players")

        assertTrue(composerPrompt.contains("create a leaderboard for football players"))
        assertTrue(composerPrompt.contains("GOOD EXAMPLE FOR A LEADERBOARD"))
        assertTrue(composerPrompt.contains("\"children\": { \"path\": \"/players\", \"componentId\": \"player_card\" }"))
        assertTrue(composerPrompt.contains("For every other UI request, create domain-appropriate sample data"))
        assertTrue(composerPrompt.contains("Do not nest component objects inside child or children"))
        assertTrue(composerPrompt.contains("Icon"))
        assertTrue(composerPrompt.contains("Image"))
        assertTrue(composerPrompt.contains("formatDate"))
        assertTrue(composerPrompt.contains("Box"))
        assertTrue(composerPrompt.contains("Circle"))
        assertTrue(composerPrompt.contains("GOOD EXAMPLE FOR A COLOR SHAPE LAYOUT"))
        assertTrue(composerPrompt.contains("Do not approximate shapes with Badge"))
    }

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
    fun parserRejectsUnsupportedComponentsBeforeRendering() {
        val messages = listOf(
            A2UiMessages.createSurface(DynamicSurfaceId),
            A2UiMessages.updateDataModel(DynamicSurfaceId, "/", buildJsonObject {}),
            A2UiMessages.updateComponents(
                DynamicSurfaceId,
                listOf(
                    buildJsonObject {
                        put("id", "root")
                        put("component", "Video")
                    }
                )
            )
        )

        val error = runCatching {
            A2UiGenerationParser.validateSupportedMessages(messages)
        }.exceptionOrNull()

        assertTrue(error!!.message!!.contains("unsupported"))
    }

    @Test
    fun parserAcceptsMessageUiCatalogComponents() {
        val messages = listOf(
            A2UiMessages.createSurface(DynamicSurfaceId),
            A2UiMessages.updateDataModel(DynamicSurfaceId, "/", buildJsonObject {}),
            A2UiMessages.updateComponents(
                DynamicSurfaceId,
                listOf(
                    buildJsonObject {
                        put("id", "root")
                        put("component", "Card")
                        put("child", "main-column")
                    },
                    buildJsonObject {
                        put("id", "main-column")
                        put("component", "Column")
                        put(
                            "children",
                            JsonArray(
                                listOf(
                                    JsonPrimitive("channel-icon"),
                                    JsonPrimitive("divider"),
                                    JsonPrimitive("msg-avatar")
                                )
                            )
                        )
                    },
                    buildJsonObject {
                        put("id", "channel-icon")
                        put("component", "Icon")
                        put("name", "info")
                    },
                    buildJsonObject {
                        put("id", "divider")
                        put("component", "Divider")
                    },
                    buildJsonObject {
                        put("id", "msg-avatar")
                        put("component", "Image")
                        putJsonObject("url") {
                            put("path", "avatar")
                        }
                        put("variant", "avatar")
                    }
                )
            )
        )

        A2UiGenerationParser.validateSupportedMessages(messages)
    }

    @Test
    fun parserAcceptsShapeCatalogComponents() {
        val messages = listOf(
            A2UiMessages.createSurface(DynamicSurfaceId),
            A2UiMessages.updateDataModel(
                DynamicSurfaceId,
                "/",
                buildJsonObject {
                    putJsonObject("art") {
                        put("backgroundColor", "#D32F2F")
                        put("rectangleColor", "#FFFFFF")
                        put("circleColor", "#FFD54F")
                        put("text", "HI")
                    }
                }
            ),
            A2UiMessages.updateComponents(
                DynamicSurfaceId,
                listOf(
                    buildJsonObject {
                        put("id", "root")
                        put("component", "Box")
                        putJsonObject("backgroundColor") {
                            put("path", "/art/backgroundColor")
                        }
                        put("child", "rectangle")
                    },
                    buildJsonObject {
                        put("id", "rectangle")
                        put("component", "Box")
                        putJsonObject("backgroundColor") {
                            put("path", "/art/rectangleColor")
                        }
                        put("child", "circle")
                    },
                    buildJsonObject {
                        put("id", "circle")
                        put("component", "Circle")
                        putJsonObject("color") {
                            put("path", "/art/circleColor")
                        }
                        putJsonObject("text") {
                            put("path", "/art/text")
                        }
                    }
                )
            )
        )

        A2UiGenerationParser.validateSupportedMessages(messages)
    }

    @Test
    fun ociSourceParsesOutputTextIntoA2UiMessages() = runTest {
        val modelText = buildJsonObject {
            put(
                "messages",
                JsonArray(
                    listOf(
                        compactJson.parseToJsonElement(A2UiMessages.createSurface(DynamicSurfaceId)),
                        compactJson.parseToJsonElement(
                            A2UiMessages.updateDataModel(DynamicSurfaceId, "/", buildJsonObject {})
                        ),
                        compactJson.parseToJsonElement(
                            A2UiMessages.updateComponents(
                                DynamicSurfaceId,
                                listOf(
                                    buildJsonObject {
                                        put("id", "root")
                                        put("component", "Text")
                                        put("text", "Hello")
                                    }
                                )
                            )
                        )
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
        assertEquals(3, generated.messages.size)
        assertTrue("createSurface" in compactJson.parseToJsonElement(generated.messages.first()).jsonObject)
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

    @Test
    fun localSourceUsesTemporaryPromptForBlankInput() = runTest {
        val generated = LocalDynamicUiMessageSource().generate("")
        val runtime = A2UiRuntime()

        runtime.processMessages(generated.messages)

        assertEquals(TemporaryPlaygroundPrompt, runtime.dataSnapshot(DynamicSurfaceId).pathString("/prompt"))
        assertEquals("Local A2UI JSON", generated.sourceLabel)
    }
}
