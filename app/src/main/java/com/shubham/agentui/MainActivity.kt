package com.shubham.agentui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shubham.agentui.ui.theme.AgentUITheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TodoSurfaceId = "todo_surface"

private val compactJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val prettyJson = Json {
    prettyPrint = true
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentUITheme(dynamicColor = false) {
                TodoA2UiApp()
            }
        }
    }
}

@Composable
private fun TodoA2UiApp() {
    val context = LocalContext.current
    val runtime = remember { A2UiRuntime() }
    val agent = remember { TodoAgent() }

    LaunchedEffect(runtime, agent) {
        runtime.actionHandler = { action ->
            val messages = agent.handle(action, runtime.dataSnapshot(TodoSurfaceId))
            runtime.processMessages(messages)

            if (action.name == "add_todo" && messages.isEmpty()) {
                Toast.makeText(context, "Add a task title first", Toast.LENGTH_SHORT).show()
            }
        }
        runtime.processMessages(agent.bootstrapMessages())
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            A2UiSurface(
                runtime = runtime,
                surfaceId = TodoSurfaceId,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            ProtocolTrace(
                messages = runtime.lastMessages,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun A2UiSurface(
    runtime: A2UiRuntime,
    surfaceId: String,
    modifier: Modifier = Modifier
) {
    val surface = runtime.surfaces[surfaceId]
    val root = surface?.components?.get("root")

    if (surface == null || root == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    A2UiComponent(
        runtime = runtime,
        surfaceId = surfaceId,
        component = root,
        scopePath = null,
        modifier = modifier
    )
}

@Composable
private fun A2UiComponent(
    runtime: A2UiRuntime,
    surfaceId: String,
    component: JsonObject,
    scopePath: String?,
    modifier: Modifier = Modifier
) {
    when (component.string("component")) {
        "Surface" -> {
            val child = component.string("child")
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    child?.let { childId ->
                        RenderChild(
                            runtime = runtime,
                            surfaceId = surfaceId,
                            childId = childId,
                            scopePath = scopePath
                        )
                    }
                }
            }
        }

        "Column" -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(component.spacingDp(default = 10).dp),
                horizontalAlignment = when (component.string("align")) {
                    "center" -> Alignment.CenterHorizontally
                    "end" -> Alignment.End
                    else -> Alignment.Start
                }
            ) {
                component.childIds().forEach { childId ->
                    RenderChild(
                        runtime = runtime,
                        surfaceId = surfaceId,
                        childId = childId,
                        scopePath = scopePath
                    )
                }
            }
        }

        "Row" -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(component.spacingDp(default = 8).dp),
                verticalAlignment = when (component.string("align")) {
                    "start" -> Alignment.Top
                    "end" -> Alignment.Bottom
                    else -> Alignment.CenterVertically
                }
            ) {
                component.childIds().forEach { childId ->
                    val child = runtime.component(surfaceId, childId) ?: return@forEach
                    val childModifier = child.int("weight")?.let { Modifier.weight(it.toFloat()) } ?: Modifier
                    A2UiComponent(
                        runtime = runtime,
                        surfaceId = surfaceId,
                        component = child,
                        scopePath = scopePath,
                        modifier = childModifier
                    )
                }
            }
        }

        "Card" -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = component.color("containerColor") ?: MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(component.spacingDp("paddingDp", default = 14).dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    component.string("child")?.let { childId ->
                        RenderChild(runtime, surfaceId, childId, scopePath)
                    }
                    component.childIds().forEach { childId ->
                        RenderChild(runtime, surfaceId, childId, scopePath)
                    }
                }
            }
        }

        "Text" -> {
            val text = runtime.resolveText(surfaceId, component["text"], scopePath)
            val done = component["struckWhen"]?.let {
                runtime.resolveValue(surfaceId, it, scopePath)?.jsonPrimitive?.booleanOrNull
            } ?: false

            Text(
                text = text,
                modifier = modifier,
                color = if (done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = component.int("maxLines") ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                style = when (component.string("variant")) {
                    "h1" -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    "h2" -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    "subtitle" -> MaterialTheme.typography.bodyMedium
                    "caption" -> MaterialTheme.typography.labelMedium
                    "label" -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    else -> MaterialTheme.typography.bodyLarge
                },
                textDecoration = if (done) TextDecoration.LineThrough else null
            )
        }

        "TextField" -> {
            val label = runtime.resolveText(surfaceId, component["label"], scopePath)
            val placeholder = runtime.resolveText(surfaceId, component["placeholder"], scopePath)
            val currentValue = runtime.resolveText(surfaceId, component["value"], scopePath)
            var text by rememberSaveable(component.string("id"), scopePath) { mutableStateOf(currentValue) }

            LaunchedEffect(currentValue) {
                if (text != currentValue) {
                    text = currentValue
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { newValue ->
                    text = newValue
                    runtime.updateBoundValue(surfaceId, component["value"], scopePath, JsonPrimitive(newValue))
                },
                modifier = modifier.fillMaxWidth(),
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        component["submitAction"]?.jsonObject?.let { action ->
                            runtime.dispatchAction(surfaceId, action, scopePath)
                        }
                    }
                )
            )
        }

        "Button" -> {
            val onClick: () -> Unit = {
                component["action"]?.jsonObject?.let { action ->
                    runtime.dispatchAction(surfaceId, action, scopePath)
                }
            }
            val buttonContent: @Composable RowScope.() -> Unit = {
                component.string("child")?.let { childId ->
                    RenderChild(runtime, surfaceId, childId, scopePath)
                } ?: Text(runtime.resolveText(surfaceId, component["text"], scopePath))
            }

            when (component.string("variant")) {
                "borderless" -> TextButton(
                    onClick = onClick,
                    modifier = modifier,
                    shape = RoundedCornerShape(8.dp)
                ) { buttonContent() }

                else -> Button(
                    onClick = onClick,
                    modifier = modifier,
                    shape = RoundedCornerShape(8.dp)
                ) { buttonContent() }
            }
        }

        "CheckBox" -> {
            val checked = runtime.resolveValue(surfaceId, component["value"], scopePath)
                ?.jsonPrimitive
                ?.booleanOrNull
                ?: false
            val label = runtime.resolveText(surfaceId, component["label"], scopePath)

            Row(
                modifier = modifier
                    .then(
                        if (label.isBlank()) {
                            Modifier.size(48.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    )
                    .clickable(role = Role.Checkbox) {
                        val newValue = !checked
                        runtime.updateBoundValue(surfaceId, component["value"], scopePath, JsonPrimitive(newValue))
                        component["action"]?.jsonObject?.let { action ->
                            runtime.dispatchAction(surfaceId, action, scopePath)
                        }
                    }
                    .semantics {
                        role = Role.Checkbox
                        contentDescription = if (label.isBlank()) "Toggle todo" else label
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { newValue ->
                        runtime.updateBoundValue(surfaceId, component["value"], scopePath, JsonPrimitive(newValue))
                        component["action"]?.jsonObject?.let { action ->
                            runtime.dispatchAction(surfaceId, action, scopePath)
                        }
                    }
                )
                if (label.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label)
                }
            }
        }

        "List" -> {
            val children = component["children"]?.jsonObject
            val dataPath = children?.string("path")
            val componentId = children?.string("componentId")
            val items = dataPath
                ?.let { runtime.resolvePath(surfaceId, it, scopePath) }
                ?.jsonArrayOrNull()
                .orEmpty()

            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (items.isEmpty()) {
                    EmptyTodosCard()
                } else {
                    items.forEachIndexed { index, _ ->
                        val itemScope = runtime.scopedPath("$dataPath/$index", scopePath)
                        componentId?.let { childId ->
                            RenderChild(runtime, surfaceId, childId, itemScope)
                        }
                    }
                }
            }
        }

        "Spacer" -> {
            Spacer(
                modifier = modifier
                    .fillMaxWidth()
                    .height(component.spacingDp("heightDp", default = 8).dp)
            )
        }
    }
}

@Composable
private fun RowScope.RenderChild(
    runtime: A2UiRuntime,
    surfaceId: String,
    childId: String,
    scopePath: String?
) {
    val child = runtime.component(surfaceId, childId) ?: return
    val childModifier = child.int("weight")?.let { Modifier.weight(it.toFloat()) } ?: Modifier
    A2UiComponent(
        runtime = runtime,
        surfaceId = surfaceId,
        component = child,
        scopePath = scopePath,
        modifier = childModifier
    )
}

@Composable
private fun RenderChild(
    runtime: A2UiRuntime,
    surfaceId: String,
    childId: String,
    scopePath: String?
) {
    runtime.component(surfaceId, childId)?.let { child ->
        A2UiComponent(
            runtime = runtime,
            surfaceId = surfaceId,
            component = child,
            scopePath = scopePath
        )
    }
}

@Composable
private fun EmptyTodosCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            text = "All clear.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun ProtocolTrace(messages: List<String>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val latest = remember(messages) {
        messages.lastOrNull()?.let { compactJson.parseToJsonElement(it) }
    }

    Surface(
        modifier = modifier,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "A2UI message trace",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (expanded && latest != null) {
                Text(
                    text = prettyJson.encodeToString(JsonElement.serializer(), latest),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

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
            A2UiMessages.createSurface(TodoSurfaceId),
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

internal object A2UiMessages {
    private const val ProtocolVersion = "v0.9"
    const val BasicCatalogId = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"

    fun createSurface(surfaceId: String): String {
        return buildJsonObject {
            put("version", ProtocolVersion)
            putJsonObject("createSurface") {
                put("surfaceId", surfaceId)
                put("catalogId", BasicCatalogId)
                putJsonObject("theme") {
                    put("primaryColor", "#256D6B")
                    put("agentDisplayName", "Todo Agent")
                }
            }
        }.toString()
    }

    fun updateDataModel(surfaceId: String, path: String, value: JsonElement): String {
        return buildJsonObject {
            put("version", ProtocolVersion)
            putJsonObject("updateDataModel") {
                put("surfaceId", surfaceId)
                put("path", path)
                put("value", value)
            }
        }.toString()
    }

    fun updateComponents(surfaceId: String, components: List<JsonObject>): String {
        return buildJsonObject {
            put("version", ProtocolVersion)
            putJsonObject("updateComponents") {
                put("surfaceId", surfaceId)
                put("components", JsonArray(components))
            }
        }.toString()
    }

    fun deleteSurface(surfaceId: String): String {
        return buildJsonObject {
            put("version", ProtocolVersion)
            putJsonObject("deleteSurface") {
                put("surfaceId", surfaceId)
            }
        }.toString()
    }
}

private fun todoComponents(): List<JsonObject> {
    return listOf(
        component(
            "root",
            "Column",
            "children" to children("hero_card", "add_card", "summary_row", "todo_list", "clear_completed")
        ),
        component("hero_card", "Card", "child" to JsonPrimitive("hero_column")),
        component(
            "hero_column",
            "Column",
            "children" to children("title", "subtitle", "agent_status")
        ),
        component("title", "Text", "text" to JsonPrimitive("A2UI Todo Agent"), "variant" to JsonPrimitive("h1")),
        component(
            "subtitle",
            "Text",
            "text" to JsonPrimitive("The agent streams protocol JSON; this Android client renders native UI."),
            "variant" to JsonPrimitive("body")
        ),
        component("agent_status", "Text", "text" to path("/agent/status"), "variant" to JsonPrimitive("caption")),
        component("add_card", "Card", "child" to JsonPrimitive("add_column")),
        component(
            "add_column",
            "Column",
            "children" to children("add_label", "add_row")
        ),
        component("add_label", "Text", "text" to JsonPrimitive("New task"), "variant" to JsonPrimitive("caption")),
        component(
            "add_row",
            "Row",
            "align" to JsonPrimitive("center"),
            "children" to children("draft_input", "add_button")
        ),
        component(
            "draft_input",
            "TextField",
            "label" to JsonPrimitive("Task title"),
            "value" to path("/draft/title"),
            "variant" to JsonPrimitive("shortText"),
            "weight" to JsonPrimitive(1)
        ),
        component("add_button_label", "Text", "text" to JsonPrimitive("Add")),
        component(
            "add_button",
            "Button",
            "child" to JsonPrimitive("add_button_label"),
            "variant" to JsonPrimitive("primary"),
            "action" to event("add_todo")
        ),
        component(
            "summary_row",
            "Row",
            "children" to children("summary", "agent_name")
        ),
        component("summary", "Text", "text" to path("/summary"), "variant" to JsonPrimitive("h2"), "weight" to JsonPrimitive(1)),
        component("agent_name", "Text", "text" to path("/agent/name"), "variant" to JsonPrimitive("caption")),
        component(
            "todo_list",
            "List",
            "children" to buildJsonObject {
                put("path", "/todos")
                put("componentId", "todo_card")
            }
        ),
        component(
            "todo_card",
            "Card",
            "child" to JsonPrimitive("todo_row")
        ),
        component(
            "todo_row",
            "Row",
            "align" to JsonPrimitive("center"),
            "children" to children("todo_done", "todo_title", "delete_todo")
        ),
        component(
            "todo_done",
            "CheckBox",
            "label" to JsonPrimitive(""),
            "value" to path("./done")
        ),
        component(
            "todo_title",
            "Text",
            "text" to path("./title"),
            "weight" to JsonPrimitive(1)
        ),
        component("delete_todo_label", "Text", "text" to JsonPrimitive("Remove")),
        component(
            "delete_todo",
            "Button",
            "child" to JsonPrimitive("delete_todo_label"),
            "variant" to JsonPrimitive("borderless"),
            "action" to event("delete_todo", "id" to path("./id"))
        ),
        component("clear_completed_label", "Text", "text" to JsonPrimitive("Clear completed")),
        component(
            "clear_completed",
            "Button",
            "child" to JsonPrimitive("clear_completed_label"),
            "action" to event("clear_completed")
        )
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

private fun getAtPath(root: JsonElement, path: String): JsonElement? {
    if (path == "/") return root
    return pathSegments(path).fold(root as JsonElement?) { current, segment ->
        when (current) {
            is JsonObject -> current[segment]
            is JsonArray -> segment.toIntOrNull()?.let { current.getOrNull(it) }
            else -> null
        }
    }
}

private fun setAtPath(current: JsonElement, segments: List<String>, value: JsonElement): JsonElement {
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

        else -> {
            setAtPath(emptyContainerFor(segments), segments, value)
        }
    }
}

private fun removeAtPath(current: JsonElement, segments: List<String>): JsonElement {
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

private fun emptyContainerFor(remainingSegments: List<String>): JsonElement {
    return if (remainingSegments.firstOrNull()?.toIntOrNull() != null) {
        JsonArray(emptyList())
    } else {
        buildJsonObject {}
    }
}

private fun pathSegments(path: String): List<String> {
    return path.trim().trim('/').split('/').filter { it.isNotBlank() }
}

private fun JsonObject.string(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.int(key: String): Int? {
    return this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
}

private fun JsonObject.spacingDp(key: String = "spacingDp", default: Int): Int {
    return int(key) ?: default
}

private fun JsonObject.childIds(): List<String> {
    val children = this["children"] ?: return emptyList()
    return when (children) {
        is JsonArray -> children.mapNotNull { it.jsonPrimitive.contentOrNull }
        is JsonObject -> children["array"]?.jsonArrayOrNull()?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        else -> emptyList()
    }
}

private fun JsonObject.color(key: String): Color? {
    val hex = string(key) ?: return null
    return hex.removePrefix("#").toLongOrNull(radix = 16)?.let { rgb ->
        Color(0xFF000000 or rgb)
    }
}

private fun JsonObject.pathString(path: String): String {
    return getAtPath(this, path)?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun Map<String, JsonElement>.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonElement.jsonArrayOrNull(): JsonArray? {
    return this as? JsonArray
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun TodoA2UiPreview() {
    AgentUITheme(dynamicColor = false) {
        TodoA2UiApp()
    }
}
