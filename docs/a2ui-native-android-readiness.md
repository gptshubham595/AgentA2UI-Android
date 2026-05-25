# A2UI Native Android Readiness

This project is set up as a native Android proof of the A2UI pattern: an agent sends declarative JSON, and Android renders it with trusted Jetpack Compose components.

## Current Target

- A2UI protocol: `v0.9`
- Catalog: `https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json`
- Android runtime: local `A2UiRuntime` in `app/src/main/java/com/shubham/agentui/A2UiRuntime.kt`
- UI implementation: Jetpack Compose and Material 3
- Message source today: local `DynamicUiAgent` fallback or OCI/LiteLLM through `OciDynamicUiMessageSource`
- Message source next: A2UI Composer output, WebSocket/SSE, A2A, AG-UI, MCP, or REST polling

## Runtime Capabilities

The local runtime is ready for the core A2UI loop:

- `createSurface`: creates a render surface and records the catalog.
- `updateComponents`: incrementally adds or replaces component definitions without clearing the existing component map.
- `updateDataModel`: writes values into the surface data model by JSON-pointer path.
- `updateDataModel` without `value`: deletes the value at the target path.
- `deleteSurface`: removes the surface, component map, and data model.
- Data binding: resolves literal values and `{ "path": "..." }` bindings.
- Relative paths: supports scoped list rendering with paths such as `./id` and `./title`.
- Actions: dispatches `{ "event": { "name": "...", "context": ... } }` back to the agent.
- Native rendering: maps known A2UI component names to Compose widgets.

## Supported Local Components

- `Column`
- `Row`
- `Card`
- `Text`
- `TextField`
- `Button`
- `CheckBox`
- `List`
- `Spacer`
- `Icon`
- `Image`
- `Divider`
- `Title`
- `DashboardCard`
- `Metric`
- `Badge`
- `DataTable`
- `PieChart`
- `BarChart`
- `FlightCard`

The renderer also has small local affordances, but Composer/agent output should stay within the Android catalog subset above unless we publish a custom catalog for Android-specific extensions.

This is enough to prove dynamic native UI generation for forms, lists, cards, and action flows. It is intentionally smaller than a full production renderer.

## AI Agent Flow

1. The agent receives a user request.
2. The agent chooses the A2UI catalog and emits JSONL messages.
3. Android receives each message and calls `A2UiRuntime.processMessage(...)`.
4. The renderer displays the `root` component using native Compose.
5. User actions are sent back to the agent with resolved context values.
6. The agent streams more `updateComponents` or `updateDataModel` messages.

The current app includes a prompt textbox. `DynamicUiMessageSources.create()` chooses the message source:

- Default: `LocalDynamicUiMessageSource` wraps `DynamicUiAgent` and returns deterministic A2UI JSON.
- Optional: `OciDynamicUiMessageSource` calls the OCI/LiteLLM `/responses` endpoint through Ktor and parses the returned A2UI JSON.
- Safety: `FallbackDynamicUiMessageSource` falls back to local JSON if the OCI request fails.

The temporary Python OCI scripts are useful for checking credentials and model access, but Android now has a native client for the same endpoint.

## Demo Modes

- **Playground**: prompt text goes to the selected message source, which returns A2UI JSON messages. Empty input becomes a default temporary-agent prompt and uses the same AI/OCI path when configured.
- **Flights JSON**: the sample flight payload is treated as received runtime data and converted into flight cards.

## Gradle Properties / BuildConfig

The app reads OCI settings from Gradle properties or environment variables and exposes them through `BuildConfig`:

```properties
A2UI_USE_OCI_AGENT=true
A2UI_OCI_OPENAI_API_KEY=your_oci_openai_bearer_token
A2UI_OCI_LITELLM_BASE_URL=https://code-internal.aiservice.us-chicago-1.oci.oraclecloud.com/20250206/app/litellm
A2UI_OCI_LITELLM_MODEL=gpt-5.5
```

For committed code, keep the key empty. With a real key, OCI is used by default; set `A2UI_USE_OCI_AGENT=false` only when you want to force the deterministic local generator. Put real keys in `~/.gradle/gradle.properties`, local environment variables, or a production secrets system.

## Using A2UI Composer

A2UI Composer can generate component JSON for the agent prompt or backend response. For this app:

1. Generate a `v0.9` A2UI surface using the Basic Catalog.
2. Keep component names within the supported local component list, or add renderer mappings.
3. Send messages in this order for first render:
   - `createSurface`
   - `updateComponents`
   - `updateDataModel`
4. Send later `updateComponents` and `updateDataModel` messages incrementally.

## Community Android Renderer Option

The `lmee/A2UI-Android` project provides a larger community Jetpack Compose renderer. It is useful if we want to move beyond this local proof quickly.

Expected integration shape:

```kotlin
// settings.gradle.kts
include(":android_compose")
project(":android_compose").projectDir = file("path/to/A2UI-Android/android_compose")

// app/build.gradle.kts
dependencies {
    implementation(project(":android_compose"))
}
```

That renderer adds broader component coverage, transport helpers, validation, media, and charts. Before adopting it in production, pin a commit, review its dependency versions against this app, and run the Android test/build pipeline.

## Production Checklist

- Decide whether to keep the local renderer or vendor `lmee/A2UI-Android`.
- Add a transport boundary: WebSocket, SSE, A2A, AG-UI, MCP, or REST.
- Validate incoming messages against the chosen catalog before rendering.
- Maintain an allowlist of supported components and local functions.
- Add renderer mappings for any Composer-generated components not listed above.
- Add error UI for unknown components, missing child references, and invalid data paths.
- Send action payloads back to the agent through the chosen transport.
- Keep user data and agent-generated JSON separate from executable code.
