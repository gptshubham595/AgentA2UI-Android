package com.shubham.agentui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicUiAgentTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun receivedFlightJsonParsesAsRuntimeData() {
        val root = json.parseToJsonElement(ReceivedFlightsJson).jsonObject
        val flights = root.getValue("flights").jsonArray

        assertEquals(3, flights.size)
        assertEquals("American Airlines", flights[0].jsonObject.getValue("airline").jsonPrimitive.content)
        assertEquals("$264", flights[1].jsonObject.getValue("price").jsonPrimitive.content)
    }

    @Test
    fun flightPromptGeneratesA2UiMessagesFromReceivedJson() {
        val agent = DynamicUiAgent()
        val runtime = A2UiRuntime()

        runtime.processMessages(agent.generate("show flight options from received json"))

        val data = runtime.dataSnapshot(DynamicSurfaceId)
        assertEquals(3, data.getValue("flights").jsonArray.size)
        assertEquals("SFO to JFK", data.pathString("/agent/summary"))
        assertNotNull(runtime.component(DynamicSurfaceId, "root"))
        assertNotNull(runtime.component(DynamicSurfaceId, "flight_list"))
        assertNotNull(runtime.component(DynamicSurfaceId, "select_button"))
    }

    @Test
    fun flightSelectionActionUsesScopedJsonData() {
        val agent = DynamicUiAgent()
        val runtime = A2UiRuntime()
        runtime.processMessages(agent.generate("flights"))
        runtime.actionHandler = { action ->
            runtime.processMessages(agent.handle(action, runtime.dataSnapshot(DynamicSurfaceId)))
        }

        val selectAction = runtime.component(DynamicSurfaceId, "select_button")!!.getValue("action").jsonObject
        runtime.dispatchAction(DynamicSurfaceId, selectAction, "/flights/1")

        val data = runtime.dataSnapshot(DynamicSurfaceId)
        assertEquals("b6-1218", data.getValue("selectedFlightId").jsonPrimitive.content)
        assertTrue(data.pathString("/agent/status").contains("JetBlue Airways"))
        assertTrue(data.pathString("/agent/status").contains("$264"))
    }

    @Test
    fun formPromptGeneratesEditableFields() {
        val runtime = A2UiRuntime()
        runtime.processMessages(DynamicUiAgent().generate("create a lead capture form"))

        assertNotNull(runtime.component(DynamicSurfaceId, "name_field"))
        assertNotNull(runtime.component(DynamicSurfaceId, "email_field"))
        assertNotNull(runtime.component(DynamicSurfaceId, "submit_button"))
        assertEquals("", runtime.dataSnapshot(DynamicSurfaceId).pathString("/form/name"))
    }

    @Test
    fun colorShapePromptGeneratesNativeShapeComponents() {
        val runtime = A2UiRuntime()
        runtime.processMessages(
            DynamicUiAgent().generate(
                "background red colour with white rectangle and yellow circle in mid and write HI"
            )
        )

        assertEquals("#D32F2F", runtime.dataSnapshot(DynamicSurfaceId).pathString("/art/backgroundColor"))
        assertEquals("#FFFFFF", runtime.dataSnapshot(DynamicSurfaceId).pathString("/art/rectangleColor"))
        assertEquals("#FFD54F", runtime.dataSnapshot(DynamicSurfaceId).pathString("/art/circleColor"))
        assertEquals("HI", runtime.dataSnapshot(DynamicSurfaceId).pathString("/art/label"))
        assertEquals("Box", runtime.component(DynamicSurfaceId, "root")!!.getValue("component").jsonPrimitive.content)
        assertEquals("Box", runtime.component(DynamicSurfaceId, "white_rectangle")!!.getValue("component").jsonPrimitive.content)
        assertEquals("Circle", runtime.component(DynamicSurfaceId, "yellow_circle")!!.getValue("component").jsonPrimitive.content)
    }

    @Test
    fun fallbackPromptGeneratesSummaryCardAndKeepsPrompt() {
        val prompt = "make a card for launch readiness"
        val runtime = A2UiRuntime()

        runtime.processMessages(DynamicUiAgent().generate(prompt))

        assertEquals(prompt, runtime.dataSnapshot(DynamicSurfaceId).pathString("/prompt"))
        assertNotNull(runtime.component(DynamicSurfaceId, "summary_card"))
        assertNotNull(runtime.component(DynamicSurfaceId, "prompt_text"))
    }

    @Test
    fun emptyPromptGeneratesTemporaryUi() {
        val runtime = A2UiRuntime()

        runtime.processMessages(DynamicUiAgent().generate(""))

        assertEquals("Temporary UI", runtime.dataSnapshot(DynamicSurfaceId).pathString("/agent/summary"))
        assertTrue(runtime.dataSnapshot(DynamicSurfaceId).pathString("/agent/status").contains("temporary"))
        assertNotNull(runtime.component(DynamicSurfaceId, "summary_card"))
    }

    @Test
    fun generatedComponentsStayWithinSupportedBasicCatalogSubset() {
        val prompts = listOf(
            "",
            "flights",
            "registration form",
            "plain summary card",
            "background red colour with white rectangle and yellow circle in mid and write HI"
        )
        prompts.forEach { prompt ->
            val components = DynamicUiAgent()
                .generate(prompt)
                .componentsFromGeneratedMessages()

            components.forEach { component ->
                val componentName = component.getValue("component").jsonPrimitive.content
                assertTrue("Unsupported component $componentName", componentName in allowedComponentKeys)
                val allowed = allowedComponentKeys.getValue(componentName)
                assertTrue(
                    "Unexpected keys for $componentName: ${component.keys - allowed}",
                    component.keys.all { it in allowed }
                )
            }
        }
    }

    private fun List<String>.componentsFromGeneratedMessages(): List<JsonObject> {
        return map { json.parseToJsonElement(it).jsonObject }
            .first { "updateComponents" in it }
            .getValue("updateComponents")
            .jsonObject
            .getValue("components")
            .let { it as JsonArray }
            .map { it.jsonObject }
    }

    private val allowedComponentKeys = mapOf(
        "Box" to setOf(
            "id",
            "component",
            "child",
            "children",
            "backgroundColor",
            "containerColor",
            "contentColor",
            "textColor",
            "widthDp",
            "heightDp",
            "minHeightDp",
            "paddingDp",
            "cornerRadiusDp",
            "contentAlignment",
            "align",
            "weight"
        ),
        "Circle" to setOf(
            "id",
            "component",
            "text",
            "child",
            "color",
            "backgroundColor",
            "contentColor",
            "textColor",
            "sizeDp",
            "paddingDp",
            "weight"
        ),
        "Column" to setOf("id", "component", "children", "justify", "align", "weight"),
        "Row" to setOf("id", "component", "children", "justify", "align", "weight"),
        "Card" to setOf("id", "component", "child", "weight"),
        "Text" to setOf("id", "component", "text", "variant", "weight"),
        "TextField" to setOf("id", "component", "label", "value", "variant", "checks", "validationRegexp", "weight"),
        "Button" to setOf("id", "component", "child", "variant", "action", "checks", "weight"),
        "CheckBox" to setOf("id", "component", "label", "value", "checks", "weight"),
        "List" to setOf("id", "component", "children", "direction", "align", "weight")
    )
}
