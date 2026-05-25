package com.shubham.agentui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
internal fun A2UiSurface(
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
        "Surface" -> SurfaceFrame(modifier, component, runtime, surfaceId, scopePath)
        "Column" -> A2UiColumn(modifier, component, runtime, surfaceId, scopePath)
        "Row" -> A2UiRow(modifier, component, runtime, surfaceId, scopePath)
        "Card" -> A2UiCard(modifier, component, runtime, surfaceId, scopePath)
        "Text" -> A2UiText(modifier, component, runtime, surfaceId, scopePath)
        "TextField" -> A2UiTextField(modifier, component, runtime, surfaceId, scopePath)
        "Button" -> A2UiButton(modifier, component, runtime, surfaceId, scopePath)
        "CheckBox" -> A2UiCheckBox(modifier, component, runtime, surfaceId, scopePath)
        "List" -> A2UiList(modifier, component, runtime, surfaceId, scopePath)
        "Spacer" -> Spacer(
            modifier = modifier
                .fillMaxWidth()
                .height(component.spacingDp("heightDp", default = 8).dp)
        )
    }
}

@Composable
private fun SurfaceFrame(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
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
            component.string("child")?.let { childId ->
                RenderChild(runtime, surfaceId, childId, scopePath)
            }
        }
    }
}

@Composable
private fun A2UiColumn(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
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
            RenderChild(runtime, surfaceId, childId, scopePath)
        }
    }
}

@Composable
private fun A2UiRow(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
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
            RenderChild(runtime, surfaceId, childId, scopePath)
        }
    }
}

@Composable
private fun A2UiCard(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
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

@Composable
private fun A2UiText(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val done = component["struckWhen"]?.let {
        runtime.resolveValue(surfaceId, it, scopePath)?.jsonPrimitive?.booleanOrNull
    } ?: false

    Text(
        text = runtime.resolveText(surfaceId, component["text"], scopePath),
        modifier = modifier,
        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun A2UiTextField(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val label = runtime.resolveText(surfaceId, component["label"], scopePath)
    val placeholder = runtime.resolveText(surfaceId, component["placeholder"], scopePath)
    val currentValue = runtime.resolveText(surfaceId, component["value"], scopePath)
    val isLongText = component.string("variant") == "longText"
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
        singleLine = !isLongText,
        minLines = if (isLongText) 3 else 1,
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

@Composable
private fun A2UiButton(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
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

@Composable
private fun A2UiCheckBox(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val checked = runtime.resolveValue(surfaceId, component["value"], scopePath)
        ?.jsonPrimitive
        ?.booleanOrNull
        ?: false
    val label = runtime.resolveText(surfaceId, component["label"], scopePath)
    val toggle: (Boolean) -> Unit = { newValue ->
        runtime.updateBoundValue(surfaceId, component["value"], scopePath, JsonPrimitive(newValue))
        component["action"]?.jsonObject?.let { action ->
            runtime.dispatchAction(surfaceId, action, scopePath)
        }
    }

    Row(
        modifier = modifier
            .then(if (label.isBlank()) Modifier.size(48.dp) else Modifier.fillMaxWidth())
            .clickable(role = Role.Checkbox) { toggle(!checked) }
            .semantics {
                role = Role.Checkbox
                contentDescription = if (label.isBlank()) "Toggle todo" else label
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = toggle)
        if (label.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }
    }
}

@Composable
private fun A2UiList(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
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
            EmptyListCard()
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
private fun EmptyListCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            text = "No items yet.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
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
