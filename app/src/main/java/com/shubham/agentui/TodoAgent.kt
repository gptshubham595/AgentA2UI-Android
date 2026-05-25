package com.shubham.agentui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal const val TodoSurfaceId = "todo_surface"

internal class TodoAgent {
    private var nextId = 4
    private var todos = listOf(
        TodoItem("1", "Read the A2UI message reference", true),
        TodoItem("2", "Stream createSurface and updateComponents", false),
        TodoItem("3", "Let native Compose render the agent UI", false)
    )
    private var status = "Surface created from local agent messages."

    fun bootstrapMessages(): List<String> {
        return listOf(
            A2UiMessages.createSurface(TodoSurfaceId, agentDisplayName = "Todo Agent"),
            A2UiMessages.updateDataModel(TodoSurfaceId, "/", todoDataModel(draft = "")),
            A2UiMessages.updateComponents(TodoSurfaceId, todoComponents())
        )
    }

    fun handle(action: A2UiAction, data: JsonObject): List<String> {
        syncFrom(data)
        val draft = data.pathString("/draft/title").trim()

        when (action.name) {
            "add_todo" -> {
                if (draft.isBlank()) return emptyList()
                todos = todos + TodoItem(nextId++.toString(), draft, false)
                status = "Agent added \"$draft\"."
                return dataModelMessages(draft = "")
            }

            "toggle_todo" -> {
                val id = action.context.string("id")
                val todo = todos.firstOrNull { it.id == id }
                status = when {
                    todo == null -> "Agent received toggle for an unknown item."
                    todo.done -> "Agent marked \"${todo.title}\" complete."
                    else -> "Agent reopened \"${todo.title}\"."
                }
                return dataModelMessages(draft = draft)
            }

            "delete_todo" -> {
                val id = action.context.string("id")
                val removed = todos.firstOrNull { it.id == id }
                todos = todos.filterNot { it.id == id }
                status = if (removed != null) {
                    "Agent removed \"${removed.title}\"."
                } else {
                    "Agent could not find that todo."
                }
                return dataModelMessages(draft = draft)
            }

            "clear_completed" -> {
                val completed = todos.count { it.done }
                todos = todos.filterNot { it.done }
                status = if (completed == 0) {
                    "Nothing completed yet."
                } else {
                    "Agent cleared $completed completed ${if (completed == 1) "item" else "items"}."
                }
                return dataModelMessages(draft = draft)
            }
        }

        return emptyList()
    }

    private fun syncFrom(data: JsonObject) {
        val incomingTodos = data["todos"]?.jsonArrayOrNull()?.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            TodoItem(
                id = obj.string("id") ?: return@mapNotNull null,
                title = obj.string("title") ?: return@mapNotNull null,
                done = obj["done"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        }

        if (incomingTodos != null) {
            todos = incomingTodos
            nextId = maxOf(nextId, (todos.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1)
        }
    }

    private fun dataModelMessages(draft: String): List<String> {
        return listOf(A2UiMessages.updateDataModel(TodoSurfaceId, "/", todoDataModel(draft)))
    }

    private fun todoDataModel(draft: String): JsonObject {
        val openCount = todos.count { !it.done }
        val completedCount = todos.size - openCount
        return buildJsonObject {
            putJsonObject("draft") {
                put("title", draft)
            }
            put("todos", JsonArray(todos.map(TodoItem::toJson)))
            put("summary", "$openCount open, $completedCount completed")
            putJsonObject("agent") {
                put("name", "Todo Agent")
                put("status", status)
            }
        }
    }
}

private data class TodoItem(
    val id: String,
    val title: String,
    val done: Boolean
) {
    fun toJson(): JsonObject {
        return buildJsonObject {
            put("id", id)
            put("title", title)
            put("done", done)
        }
    }
}

private fun todoComponents(): List<JsonObject> {
    return listOf(
        component("root", "Column", "children" to children("hero_card", "add_card", "summary_row", "todo_list", "clear_completed")),
        component("hero_card", "Card", "child" to JsonPrimitive("hero_column")),
        component("hero_column", "Column", "children" to children("title", "subtitle", "agent_status")),
        component("title", "Text", "text" to JsonPrimitive("A2UI Todo Agent"), "variant" to JsonPrimitive("h1")),
        component("subtitle", "Text", "text" to JsonPrimitive("The agent streams protocol JSON; this Android client renders native UI."), "variant" to JsonPrimitive("body")),
        component("agent_status", "Text", "text" to path("/agent/status"), "variant" to JsonPrimitive("caption")),
        component("add_card", "Card", "child" to JsonPrimitive("add_column")),
        component("add_column", "Column", "children" to children("add_label", "add_row")),
        component("add_label", "Text", "text" to JsonPrimitive("New task"), "variant" to JsonPrimitive("caption")),
        component("add_row", "Row", "align" to JsonPrimitive("center"), "children" to children("draft_input", "add_button")),
        component("draft_input", "TextField", "label" to JsonPrimitive("Task title"), "value" to path("/draft/title"), "variant" to JsonPrimitive("shortText"), "weight" to JsonPrimitive(1)),
        component("add_button_label", "Text", "text" to JsonPrimitive("Add")),
        component("add_button", "Button", "child" to JsonPrimitive("add_button_label"), "variant" to JsonPrimitive("primary"), "action" to event("add_todo")),
        component("summary_row", "Row", "children" to children("summary", "agent_name")),
        component("summary", "Text", "text" to path("/summary"), "variant" to JsonPrimitive("h2"), "weight" to JsonPrimitive(1)),
        component("agent_name", "Text", "text" to path("/agent/name"), "variant" to JsonPrimitive("caption")),
        component("todo_list", "List", "children" to buildJsonObject {
            put("path", "/todos")
            put("componentId", "todo_card")
        }),
        component("todo_card", "Card", "child" to JsonPrimitive("todo_row")),
        component("todo_row", "Row", "align" to JsonPrimitive("center"), "children" to children("todo_done", "todo_title", "delete_todo")),
        component("todo_done", "CheckBox", "label" to JsonPrimitive(""), "value" to path("./done"), "action" to event("toggle_todo", "id" to path("./id"))),
        component("todo_title", "Text", "text" to path("./title"), "weight" to JsonPrimitive(1)),
        component("delete_todo_label", "Text", "text" to JsonPrimitive("Remove")),
        component("delete_todo", "Button", "child" to JsonPrimitive("delete_todo_label"), "variant" to JsonPrimitive("borderless"), "action" to event("delete_todo", "id" to path("./id"))),
        component("clear_completed_label", "Text", "text" to JsonPrimitive("Clear completed")),
        component("clear_completed", "Button", "child" to JsonPrimitive("clear_completed_label"), "action" to event("clear_completed"))
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
