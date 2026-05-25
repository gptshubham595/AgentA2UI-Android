package com.shubham.agentui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

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
        "Box" -> A2UiBox(modifier, component, runtime, surfaceId, scopePath)
        "Circle" -> A2UiCircle(modifier, component, runtime, surfaceId, scopePath)
        "Column" -> A2UiColumn(modifier, component, runtime, surfaceId, scopePath)
        "Row" -> A2UiRow(modifier, component, runtime, surfaceId, scopePath)
        "Card" -> A2UiCard(modifier, component, runtime, surfaceId, scopePath)
        "Text" -> A2UiText(modifier, component, runtime, surfaceId, scopePath)
        "TextField" -> A2UiTextField(modifier, component, runtime, surfaceId, scopePath)
        "Button" -> A2UiButton(modifier, component, runtime, surfaceId, scopePath)
        "CheckBox" -> A2UiCheckBox(modifier, component, runtime, surfaceId, scopePath)
        "List" -> A2UiList(modifier, component, runtime, surfaceId, scopePath)
        "Icon" -> A2UiIcon(modifier, component)
        "Image" -> A2UiImage(modifier, component, runtime, surfaceId, scopePath)
        "Divider" -> HorizontalDivider(modifier = modifier.fillMaxWidth())
        "Title" -> A2UiTitle(modifier, component, runtime, surfaceId, scopePath)
        "DashboardCard" -> A2UiDashboardCard(modifier, component, runtime, surfaceId, scopePath)
        "Metric" -> A2UiMetric(modifier, component, runtime, surfaceId, scopePath)
        "Badge" -> A2UiBadge(modifier, component, runtime, surfaceId, scopePath)
        "DataTable" -> A2UiDataTable(modifier, component, runtime, surfaceId, scopePath)
        "PieChart" -> A2UiValueListChart(modifier, component, runtime, surfaceId, scopePath)
        "BarChart" -> A2UiBarChart(modifier, component, runtime, surfaceId, scopePath)
        "FlightCard" -> A2UiFlightCard(modifier, component, runtime, surfaceId, scopePath)
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
            component.childId()?.let { childId ->
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
    val shape = RoundedCornerShape(component.spacingDp("cornerRadiusDp", default = 0).dp)
    Column(
        modifier = modifier
            .sizedContainer(component)
            .maybeBackground(
                color = component.color("backgroundColor", runtime, surfaceId, scopePath),
                shape = shape
            ),
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
        component.childTemplate()?.let { template ->
            RenderTemplatedChildren(runtime, surfaceId, template, scopePath)
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
    val shape = RoundedCornerShape(component.spacingDp("cornerRadiusDp", default = 0).dp)
    Row(
        modifier = modifier
            .sizedContainer(component)
            .maybeBackground(
                color = component.color("backgroundColor", runtime, surfaceId, scopePath),
                shape = shape
            ),
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
        component.childTemplate()?.let { template ->
            RenderTemplatedChildren(runtime, surfaceId, template, scopePath)
        }
    }
}

@Composable
private fun A2UiBox(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val shape = RoundedCornerShape(component.spacingDp("cornerRadiusDp", default = 0).dp)
    val contentColor = component.color("contentColor", runtime, surfaceId, scopePath)
        ?: component.color("textColor", runtime, surfaceId, scopePath)
        ?: LocalContentColor.current
    Box(
        modifier = modifier
            .sizedContainer(component)
            .clip(shape)
            .background(
                component.color("backgroundColor", runtime, surfaceId, scopePath)
                    ?: component.color("containerColor", runtime, surfaceId, scopePath)
                    ?: Color.Transparent
            )
            .padding(component.spacingDp("paddingDp", default = 0).dp),
        contentAlignment = component.contentAlignment()
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            component.childId()?.let { childId ->
                RenderChild(runtime, surfaceId, childId, scopePath)
            }
            component.childIds().forEach { childId ->
                RenderChild(runtime, surfaceId, childId, scopePath)
            }
        }
    }
}

@Composable
private fun A2UiCircle(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val size = component.spacingDp("sizeDp", default = 88).dp
    val contentColor = component.color("contentColor", runtime, surfaceId, scopePath)
        ?: component.color("textColor", runtime, surfaceId, scopePath)
        ?: MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                component.color("color", runtime, surfaceId, scopePath)
                    ?: component.color("backgroundColor", runtime, surfaceId, scopePath)
                    ?: MaterialTheme.colorScheme.primaryContainer
            )
            .padding(component.spacingDp("paddingDp", default = 0).dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            component.childId()?.let { childId ->
                RenderChild(runtime, surfaceId, childId, scopePath)
            } ?: Text(
                text = runtime.resolveText(surfaceId, component.textValue(), scopePath),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
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
            containerColor = component.color("containerColor", runtime, surfaceId, scopePath)
                ?: MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(component.spacingDp("paddingDp", default = 14).dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            component.childId()?.let { childId ->
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
    val contentColor = LocalContentColor.current
    val explicitColor = component.color("textColor", runtime, surfaceId, scopePath)
        ?: component.color("color", runtime, surfaceId, scopePath)
    val done = component["struckWhen"]?.let {
        runtime.resolveValue(surfaceId, it, scopePath)?.primitiveBooleanOrNull()
    } ?: false

    Text(
        text = runtime.resolveText(surfaceId, component.textValue(), scopePath),
        modifier = modifier,
        color = if (done) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else if (explicitColor != null) {
            explicitColor
        } else if (contentColor == Color.Unspecified) {
            MaterialTheme.colorScheme.onSurface
        } else {
            contentColor
        },
        maxLines = component.int("maxLines") ?: Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        style = when (component.string("variant")) {
            "h1" -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            "h2" -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            "h3" -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            "h4" -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            "body" -> MaterialTheme.typography.bodyLarge
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
        component.childId()?.let { childId ->
            RenderChild(runtime, surfaceId, childId, scopePath)
        } ?: Text(runtime.resolveText(surfaceId, component.textValue(), scopePath))
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
        ?.primitiveBooleanOrNull()
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
                contentDescription = if (label.isBlank()) "Toggle option" else label
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
private fun RenderTemplatedChildren(
    runtime: A2UiRuntime,
    surfaceId: String,
    template: ChildTemplate,
    scopePath: String?
) {
    val items = runtime.resolvePath(surfaceId, template.path, scopePath)
        ?.jsonArrayOrNull()
        .orEmpty()

    items.forEachIndexed { index, _ ->
        RenderChild(
            runtime = runtime,
            surfaceId = surfaceId,
            childId = template.componentId,
            scopePath = runtime.scopedPath("${template.path}/$index", scopePath)
        )
    }
}

@Composable
private fun RowScope.RenderTemplatedChildren(
    runtime: A2UiRuntime,
    surfaceId: String,
    template: ChildTemplate,
    scopePath: String?
) {
    val items = runtime.resolvePath(surfaceId, template.path, scopePath)
        ?.jsonArrayOrNull()
        .orEmpty()

    items.forEachIndexed { index, _ ->
        RenderChild(
            runtime = runtime,
            surfaceId = surfaceId,
            childId = template.componentId,
            scopePath = runtime.scopedPath("${template.path}/$index", scopePath)
        )
    }
}

@Composable
private fun A2UiIcon(
    modifier: Modifier,
    component: JsonObject
) {
    val label = component.string("name").orEmpty().take(1).uppercase().ifBlank { "i" }
    Box(
        modifier = modifier
            .size(component.spacingDp("sizeDp", default = 30).dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun A2UiImage(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val isAvatar = component.string("variant") == "avatar"
    val size = component.spacingDp("sizeDp", default = if (isAvatar) 40 else 160).dp
    AsyncImage(
        model = runtime.resolveText(surfaceId, component["url"], scopePath),
        contentDescription = runtime.resolveText(surfaceId, component["contentDescription"], scopePath),
        modifier = modifier.then(
            if (isAvatar) {
                Modifier.size(size).clip(CircleShape)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(size)
                    .clip(RoundedCornerShape(8.dp))
            }
        ),
        contentScale = when (component.string("fit")) {
            "contain" -> ContentScale.Fit
            else -> ContentScale.Crop
        }
    )
}

@Composable
private fun A2UiTitle(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val variant = when (component.string("level")) {
        "h1" -> "h1"
        "h2" -> "h2"
        "h3" -> "h3"
        else -> "h2"
    }
    A2UiText(
        modifier = modifier,
        component = JsonObject(component.toMutableMap().apply {
            put("component", JsonPrimitive("Text"))
            put("variant", JsonPrimitive(variant))
        }),
        runtime = runtime,
        surfaceId = surfaceId,
        scopePath = scopePath
    )
}

@Composable
private fun A2UiDashboardCard(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = runtime.resolveText(surfaceId, component["title"], scopePath),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            runtime.resolveText(surfaceId, component["subtitle"], scopePath)
                .takeIf { it.isNotBlank() }
                ?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            component.childId()?.let { childId ->
                RenderChild(runtime, surfaceId, childId, scopePath)
            }
        }
    }
}

@Composable
private fun A2UiMetric(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = runtime.resolveText(surfaceId, component["label"], scopePath),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = runtime.resolveText(surfaceId, component["value"], scopePath),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        runtime.resolveText(surfaceId, component["trendValue"], scopePath)
            .takeIf { it.isNotBlank() }
            ?.let { trend ->
                Text(
                    text = trend,
                    color = trendColor(component.string("trend")),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
    }
}

@Composable
private fun A2UiBadge(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = badgeColor(component.string("variant"))
    ) {
        Text(
            text = runtime.resolveText(surfaceId, component["text"], scopePath),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun A2UiDataTable(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val columns = (runtime.resolveValue(surfaceId, component["columns"], scopePath) as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
    val rows = (runtime.resolveValue(surfaceId, component["rows"], scopePath) as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    columns.forEach { column ->
                        val key = column.string("key").orEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = column.string("label").orEmpty(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = row[key]?.primitiveStringOrNull().orEmpty(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun A2UiValueListChart(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val data = (runtime.resolveValue(surfaceId, component["data"], scopePath) as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(item.color("color") ?: MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = item.string("label").orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = item["value"]?.primitiveStringOrNull().orEmpty(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun A2UiBarChart(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    val data = (runtime.resolveValue(surfaceId, component["data"], scopePath) as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }
    val maxValue = data.maxOfOrNull { it.float("value") ?: 0f }?.takeIf { it > 0f } ?: 1f
    val barColor = component.color("color", runtime, surfaceId, scopePath)
        ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.forEach { item ->
            val value = item.float("value") ?: 0f
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.string("label").orEmpty(), style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "${component.string("valuePrefix").orEmpty()}${item["value"]?.primitiveStringOrNull().orEmpty()}${component.string("valueSuffix").orEmpty()}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((value / maxValue).coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun A2UiFlightCard(
    modifier: Modifier,
    component: JsonObject,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = runtime.resolveText(surfaceId, component["airlineLogo"], scopePath),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = runtime.resolveText(surfaceId, component["airline"], scopePath),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = runtime.resolveText(surfaceId, component["flightNumber"], scopePath),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(runtime.resolveText(surfaceId, component["origin"], scopePath), style = MaterialTheme.typography.titleLarge)
                Text(runtime.resolveText(surfaceId, component["duration"], scopePath), style = MaterialTheme.typography.labelMedium)
                Text(runtime.resolveText(surfaceId, component["destination"], scopePath), style = MaterialTheme.typography.titleLarge)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(runtime.resolveText(surfaceId, component["departureTime"], scopePath))
                Text(runtime.resolveText(surfaceId, component["arrivalTime"], scopePath))
                Text(runtime.resolveText(surfaceId, component["price"], scopePath), fontWeight = FontWeight.Bold)
            }
            Text(
                text = "${runtime.resolveText(surfaceId, component["date"], scopePath)} - ${runtime.resolveText(surfaceId, component["status"], scopePath)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    return this[key]?.primitiveStringOrNull()?.toIntOrNull()
}

private fun JsonObject.float(key: String): Float? {
    return this[key]?.primitiveStringOrNull()?.toFloatOrNull()
}

private fun JsonObject.spacingDp(key: String = "spacingDp", default: Int): Int {
    return int(key) ?: default
}

private fun Modifier.sizedContainer(component: JsonObject): Modifier {
    var sized = this
    val width = component.int("widthDp")
    val height = component.int("heightDp")
    val minHeight = component.int("minHeightDp")

    sized = if (width != null) sized.width(width.dp) else sized.fillMaxWidth()
    if (height != null) sized = sized.height(height.dp)
    if (minHeight != null) sized = sized.defaultMinSize(minHeight = minHeight.dp)
    return sized
}

private fun Modifier.maybeBackground(color: Color?, shape: RoundedCornerShape): Modifier {
    return if (color == null) {
        this
    } else {
        clip(shape).background(color)
    }
}

private fun JsonObject.contentAlignment(): Alignment {
    return when (string("contentAlignment") ?: string("align")) {
        "topStart" -> Alignment.TopStart
        "topCenter" -> Alignment.TopCenter
        "topEnd" -> Alignment.TopEnd
        "centerStart" -> Alignment.CenterStart
        "centerEnd" -> Alignment.CenterEnd
        "bottomStart" -> Alignment.BottomStart
        "bottomCenter" -> Alignment.BottomCenter
        "bottomEnd" -> Alignment.BottomEnd
        "center" -> Alignment.Center
        else -> Alignment.Center
    }
}

private fun JsonObject.childIds(): List<String> {
    val children = this["children"] ?: return emptyList()
    return when (children) {
        is JsonArray -> children.mapNotNull { it.childReferenceId() }
        is JsonObject -> children["array"]?.jsonArrayOrNull()?.mapNotNull { it.childReferenceId() }.orEmpty()
        else -> emptyList()
    }
}

private fun JsonObject.childId(): String? {
    return this["child"]?.childReferenceId()
}

private fun JsonObject.childTemplate(): ChildTemplate? {
    val children = this["children"] as? JsonObject ?: return null
    val path = children.string("path") ?: return null
    val componentId = children.string("componentId") ?: return null
    return ChildTemplate(path = path, componentId = componentId)
}

private data class ChildTemplate(
    val path: String,
    val componentId: String
)

private fun JsonElement.childReferenceId(): String? {
    return when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> string("id")
        else -> null
    }
}

private fun JsonObject.textValue(): JsonElement? {
    return this["text"] ?: this["label"] ?: this["title"] ?: this["value"] ?: this["content"]
}

private fun JsonObject.color(key: String): Color? {
    return string(key)?.asColorOrNull()
}

private fun JsonObject.color(
    key: String,
    runtime: A2UiRuntime,
    surfaceId: String,
    scopePath: String?
): Color? {
    return runtime.resolveText(surfaceId, this[key], scopePath).asColorOrNull()
}

private fun String.asColorOrNull(): Color? {
    val hex = trim().removePrefix("#")
    return hex.takeIf { it.length == 6 }?.toLongOrNull(radix = 16)?.let { rgb ->
        Color(0xFF000000 or rgb)
    }
}

@Composable
private fun trendColor(trend: String?): Color {
    return when (trend) {
        "up" -> Color(0xFF15803D)
        "down" -> Color(0xFFB91C1C)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun badgeColor(variant: String?): Color {
    return when (variant) {
        "success" -> Color(0xFFDCFCE7)
        "warning" -> Color(0xFFFEF3C7)
        "error" -> Color(0xFFFEE2E2)
        "info" -> Color(0xFFDBEAFE)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}
