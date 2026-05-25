# AgentUI A2UI Todo Sample

This Android sample shows the Agent to User Interface pattern with a small local todo agent.

The app is aligned to the official A2UI v0.9 message flow. An AI agent or A2UI Composer can generate the same kind of JSON messages, and this native Android client maps those messages to trusted Jetpack Compose UI.

The app starts by processing three A2UI messages:

- `createSurface` creates a render target for the todo UI.
- `updateDataModel` seeds `/draft`, `/todos`, `/summary`, and `/agent`.
- `updateComponents` declares the native components to render.

It also supports `deleteSurface`, incremental `updateComponents`, JSON-pointer data model updates, and path deletion when an `updateDataModel` omits `value`.

User actions stay on the same loop. Buttons dispatch event actions back to `TodoAgent`, checkbox edits update the local data model through two-way binding, and the agent responds with new `updateDataModel` messages. The bottom trace panel shows the latest protocol message processed by the client.

## A2UI Native Android Readiness

- Protocol target: A2UI `v0.9`.
- Catalog target: `https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json`.
- AI authoring path: A2UI Composer or an LLM-backed agent emits JSONL messages.
- Android rendering path: `A2UiRuntime` parses messages, stores surfaces/components/data, and `A2UiComponent` renders native Compose widgets.
- Supported Basic Catalog components in this sample: `Column`, `Row`, `Card`, `Text`, `TextField`, `Button`, `CheckBox`, and `List`.
- Production renderer option: the community `lmee/A2UI-Android` Jetpack Compose renderer can be vendored as an `android_compose` Gradle module if we want 20+ components, WebSocket/SSE transport, validation, media, and chart support.

See [docs/a2ui-native-android-readiness.md](/Users/shukugup/personal/a2ui/docs/a2ui-native-android-readiness.md) for the integration checklist.

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

- `app/src/main/java/com/shubham/agentui/MainActivity.kt` contains the sample agent, A2UI message builders, tiny renderer, and Compose UI.
- `app/src/main/java/com/shubham/agentui/ui/theme/Theme.kt` contains the sample Material theme.
