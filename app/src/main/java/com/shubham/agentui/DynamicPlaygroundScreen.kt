package com.shubham.agentui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shubham.agentui.ui.theme.AgentUITheme
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DynamicA2UiApp() {
    val runtime = remember { A2UiRuntime() }
    val dynamicAgent = remember { DynamicUiAgent() }
    val messageSource = remember { DynamicUiMessageSources.create() }
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(A2UiDemoMode.Playground) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var liveMode by rememberSaveable { mutableStateOf(true) }
    var isGenerating by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf(messageSource.sourceLabel) }
    var warning by rememberSaveable { mutableStateOf<String?>(null) }
    var generationId by remember { mutableIntStateOf(0) }

    suspend fun generatePlayground(requestPrompt: String) {
        val requestId = ++generationId
        isGenerating = true
        warning = null
        status = "Generating JSON..."
        try {
            val generated = messageSource.generate(requestPrompt)
            if (requestId == generationId) {
                runtime.processMessages(generated.messages)
                status = generated.sourceLabel
                warning = generated.warning
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (requestId == generationId) {
                status = "Generation failed"
                warning = error.message.orEmpty().ifBlank { error::class.simpleName }
            }
        } finally {
            if (requestId == generationId) {
                isGenerating = false
            }
        }
    }

    fun renderFlightsDemo() {
        generationId++
        isGenerating = false
        warning = null
        status = "Flights JSON -> A2UI native cards"
        runtime.processMessages(dynamicAgent.generate("Show flight options from received JSON"))
    }

    LaunchedEffect(runtime, dynamicAgent) {
        runtime.actionHandler = { action ->
            runtime.processMessages(dynamicAgent.handle(action, runtime.dataSnapshot(DynamicSurfaceId)))
        }
    }

    LaunchedEffect(prompt, liveMode, mode) {
        if (mode == A2UiDemoMode.Playground && liveMode) {
            delay(700)
            generatePlayground(prompt)
        }
    }

    LaunchedEffect(mode) {
        when (mode) {
            A2UiDemoMode.Playground -> {
                if (!liveMode) generatePlayground(prompt)
            }

            A2UiDemoMode.Flights -> renderFlightsDemo()
        }
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
            DemoControls(
                selectedMode = mode,
                onModeChange = { mode = it },
                prompt = prompt,
                onPromptChange = { prompt = it },
                liveMode = liveMode,
                onLiveModeChange = { liveMode = it },
                isGenerating = isGenerating,
                status = status,
                warning = warning,
                onGenerate = {
                    when (mode) {
                        A2UiDemoMode.Playground -> scope.launch { generatePlayground(prompt) }
                        A2UiDemoMode.Flights -> renderFlightsDemo()
                    }
                }
            )

            A2UiSurface(
                runtime = runtime,
                surfaceId = DynamicSurfaceId,
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

private enum class A2UiDemoMode(val title: String) {
    Playground("Playground"),
    Flights("Flights JSON")
}

@Composable
private fun DemoControls(
    selectedMode: A2UiDemoMode,
    onModeChange: (A2UiDemoMode) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    liveMode: Boolean,
    onLiveModeChange: (Boolean) -> Unit,
    isGenerating: Boolean,
    status: String,
    warning: String?,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "A2UI playground",
            style = MaterialTheme.typography.titleMedium
        )
        ModeSelector(
            selectedMode = selectedMode,
            onModeChange = onModeChange
        )
        if (selectedMode == A2UiDemoMode.Playground) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Prompt") },
                placeholder = { Text("create a leaderboard for football players") },
                minLines = 2
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onGenerate,
                enabled = !isGenerating
            ) {
                Text(buttonLabel(selectedMode, isGenerating))
            }
            if (selectedMode == A2UiDemoMode.Playground) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = liveMode,
                        onCheckedChange = onLiveModeChange
                    )
                    Text(
                        text = "Live",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        warning?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ModeSelector(
    selectedMode: A2UiDemoMode,
    onModeChange: (A2UiDemoMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        A2UiDemoMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            TextButton(
                onClick = { onModeChange(mode) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = mode.title,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun buttonLabel(mode: A2UiDemoMode, isGenerating: Boolean): String {
    if (isGenerating) return "Generating..."
    return when (mode) {
        A2UiDemoMode.Playground -> "Generate JSON UI"
        A2UiDemoMode.Flights -> "Render flights JSON"
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun DynamicA2UiPreview() {
    AgentUITheme(dynamicColor = false) {
        DynamicA2UiApp()
    }
}
