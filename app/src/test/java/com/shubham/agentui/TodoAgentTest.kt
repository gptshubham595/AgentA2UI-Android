package com.shubham.agentui

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoAgentTest {
    @Test
    fun todoAgentBootstrapsDynamicA2UiSurface() {
        val runtime = A2UiRuntime()

        runtime.processMessages(TodoAgent().bootstrapMessages())

        assertNotNull(runtime.component(TodoSurfaceId, "root"))
        assertNotNull(runtime.component(TodoSurfaceId, "todo_list"))
        assertEquals(3, runtime.dataSnapshot(TodoSurfaceId).getValue("todos").jsonArray.size)
    }

    @Test
    fun addTodoActionReadsDraftDataAndRefreshesModel() {
        val agent = TodoAgent()
        val runtime = A2UiRuntime()
        runtime.processMessages(agent.bootstrapMessages())
        runtime.updateDataModel(TodoSurfaceId, "/draft/title", JsonPrimitive("Test dynamic todo UI"))

        runtime.processMessages(agent.handle(A2UiAction(TodoSurfaceId, "add_todo", emptyMap()), runtime.dataSnapshot(TodoSurfaceId)))

        val data = runtime.dataSnapshot(TodoSurfaceId)
        assertEquals("", data.pathString("/draft/title"))
        assertEquals(4, data.getValue("todos").jsonArray.size)
        assertTrue(data.pathString("/agent/status").contains("Test dynamic todo UI"))
    }

    @Test
    fun checkboxActionUsesScopedTodoData() {
        val agent = TodoAgent()
        val runtime = A2UiRuntime()
        runtime.processMessages(agent.bootstrapMessages())
        runtime.updateDataModel(TodoSurfaceId, "/todos/1/done", JsonPrimitive(true))
        runtime.actionHandler = {
            runtime.processMessages(agent.handle(it, runtime.dataSnapshot(TodoSurfaceId)))
        }

        val action = runtime.component(TodoSurfaceId, "todo_done")!!.getValue("action").jsonObject
        runtime.dispatchAction(TodoSurfaceId, action, "/todos/1")

        assertTrue(runtime.dataSnapshot(TodoSurfaceId).pathString("/agent/status").contains("complete"))
    }
}
