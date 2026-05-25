# AgentUI A2UI Playground

This Android sample shows the Agent to User Interface pattern as a live native UI playground.

The app is aligned to the official A2UI v0.9 message flow. The local generator is the fallback; when configured, Android calls the OCI/LiteLLM endpoint directly and renders the returned A2UI JSON.

Use the top tabs to switch between both demos:

- **Playground**: type a prompt, receive A2UI JSON, and render a new native UI. Empty input is sent as a default temporary-agent prompt. **Live** mode debounces typing and regenerates automatically.
- **Flights JSON**: renders the provided flights JSON as native cards through generated A2UI messages.

The app renders by processing A2UI messages:

- `deleteSurface` clears the previous generated UI.
- `createSurface` creates a render target.
- `updateDataModel` seeds JSON data such as `/flights`, `/form`, `/prompt`, and `/agent`.
- `updateComponents` declares the native components to render.

It also supports `deleteSurface`, incremental `updateComponents`, JSON-pointer data model updates, and path deletion when an `updateDataModel` omits `value`.

User actions stay on the same loop. Buttons dispatch event actions back to the agent, text fields update the local data model through two-way binding, and the agent responds with new `updateDataModel` messages. The bottom trace panel shows the latest generated A2UI JSON batch.

## A2UI Native Android Readiness

- Protocol target: A2UI `v0.9`.
- Catalog target: `https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json`.
- AI authoring path: A2UI Composer, the local prompt generator, or OCI/LiteLLM emits JSON messages.
- Android rendering path: `A2UiRuntime` parses messages, stores surfaces/components/data, and `A2UiComponent` renders native Compose widgets.
- Supported Android catalog components in this sample: `Column`, `Row`, `Card`, `Text`, `TextField`, `Button`, `CheckBox`, `List`, `Spacer`, `Icon`, `Image`, `Divider`, `Title`, `DashboardCard`, `Metric`, `Badge`, `DataTable`, `PieChart`, `BarChart`, and `FlightCard`.
- Production renderer option: the community `lmee/A2UI-Android` Jetpack Compose renderer can be vendored as an `android_compose` Gradle module if we want 20+ components, WebSocket/SSE transport, validation, media, and chart support.

See [docs/a2ui-native-android-readiness.md](/Users/shukugup/personal/a2ui/docs/a2ui-native-android-readiness.md) for the integration checklist.

## OCI / GPT Backend

The temporary Python OCI scripts are not packaged into Android. The Android app now has its own Ktor client that calls the same LiteLLM `/responses` endpoint when an API key is available:

```properties
A2UI_USE_OCI_AGENT=true
A2UI_OCI_OPENAI_API_KEY=your_oci_openai_bearer_token
A2UI_OCI_LITELLM_BASE_URL=https://code-internal.aiservice.us-chicago-1.oci.oraclecloud.com/20250206/app/litellm
A2UI_OCI_LITELLM_MODEL=gpt-5.5
```

The checked-in `gradle.properties` keeps the key empty and defaults to the local generator. With a real key, the playground uses OCI by default; set `A2UI_USE_OCI_AGENT=false` to force local fallback. Prefer `~/.gradle/gradle.properties` or environment variables with the same names for real credentials.

## Dependencies Used

- Jetpack Compose UI and Material 3 for native rendering.
- Coil Compose for loading generated image/avatar/logo URLs.
- Kotlinx Serialization JSON for A2UI message/data parsing.
- Ktor Client Android/Core/Content Negotiation/Logging for the optional OCI backend.
- JUnit, Coroutines Test, and Ktor MockEngine for unit tests.
- Hilt, Room, DataStore, WorkManager, and Navigation 3 are present in the app template but are not required for the current A2UI renderer path.

## Run

```bash
./gradlew :app:assembleDebug
```

Open the project in Android Studio or install `app/build/outputs/apk/debug/app-debug.apk` on a device/emulator.

## Test

```bash
./gradlew :app:testDebugUnitTest
```

## Main Files

- `app/src/main/java/com/shubham/agentui/MainActivity.kt` contains the short Activity shell.
- `app/src/main/java/com/shubham/agentui/DynamicPlaygroundScreen.kt` contains the live prompt playground.
- `app/src/main/java/com/shubham/agentui/DynamicUiMessageSource.kt` contains local, OCI, parser, and fallback message sources.
- `app/src/main/java/com/shubham/agentui/A2UiRenderer.kt` maps A2UI components to Compose widgets.
- `app/src/main/java/com/shubham/agentui/A2UiRuntime.kt` processes A2UI messages and keeps surface/data state.
- `app/src/main/java/com/shubham/agentui/DynamicUiAgent.kt` turns a prompt or received JSON into A2UI messages.
- `app/src/main/java/com/shubham/agentui/A2UiMessages.kt` contains shared A2UI message builders.
- `app/src/main/java/com/shubham/agentui/ui/theme/Theme.kt` contains the sample Material theme.
