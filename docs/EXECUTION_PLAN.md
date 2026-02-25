# Card Game Advisor — Execution Plan

This document breaks the architecture from `ARCHITECTURE.md` into ordered, parallelizable work phases. **Phase 0** must be completed first by a single developer — it sets up the build config, shared data types, and every module interface + stub so that all other developers can branch off and work independently starting in Phase 1.

---

## Phase 0 — Foundation (single developer, sequential)

**Goal:** Build config, shared model, all interfaces, all stubs/mocks, DI skeleton, and app shell. After this merges, every developer has compilable contracts to code against.

### 0.1 — Build Configuration

| File | Action | Details |
|------|--------|---------|
| `gradle/libs.versions.toml` | Modify | Add version entries: `navigationCompose`, `lifecycleViewmodelCompose`, `coroutines`, `tflite`, `tfliteSupport`, `datCore`, `datCamera`, `datMockdevice` |
| `settings.gradle.kts` | Modify | Add GitHub Packages Maven repo for DAT SDK (`https://maven.pkg.github.com/facebook/meta-wearables-dat-android`), gated on `GITHUB_TOKEN` |
| `app/build.gradle.kts` | Modify | Bump `minSdk` to 29. Add all new `implementation`/`debugImplementation` deps using version catalog aliases |
| `AndroidManifest.xml` | Modify | Add `android:name=".GettingRichApp"`, add permissions (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `INTERNET`), add DAT `APPLICATION_ID` meta-data |

### 0.2 — Shared Model (`model/`)

Create `app/src/main/java/com/example/gettingrichapp/model/`:

| File | Contents |
|------|----------|
| `Card.kt` | `Suit` enum, `CardValue` enum (with `numericValue: Int`, `hiLoValue: Int`), `Card` data class (`value: CardValue`, `suit: Suit`) |
| `HandEvaluation.kt` | `HandEvaluation` data class (`hardTotal: Int`, `softTotal: Int`, `isSoft: Boolean`, `isPair: Boolean`, `isBlackjack: Boolean`, `pairValue: CardValue?`) |
| `Advice.kt` | `Action` enum (`HIT`, `STAND`, `DOUBLE`, `SPLIT`, `SURRENDER`), `Advice` data class (`action: Action`, `playerCards: List<Card>`, `dealerUpcard: Card?`, `handEvaluation: HandEvaluation?`) |

### 0.3 — All Interfaces + State Types

Create one file per module with the interface and any associated sealed/data classes:

| Package | Files | Contents |
|---------|-------|----------|
| `glasses/` | `ConnectionState.kt`, `GlassesConnection.kt` | `ConnectionState` sealed class (`Disconnected`, `Searching`, `Connected`, `Error(message)`), `GlassesConnection` interface |
| `camera/` | `FrameData.kt`, `FrameProvider.kt` | `FrameData` data class, `StreamState` enum, `FrameProvider` interface |
| `detection/` | `DetectionResult.kt`, `CardDetector.kt` | `DetectedCard` data class, `DetectionResult` data class, `CardDetector` interface |
| `counting/` | `CountState.kt`, `CardCounter.kt` | `CountState` data class (with computed `trueCount`), `CardCounter` interface |
| `strategy/` | `StrategyEngine.kt` | `StrategyEngine` interface |
| `audio/` | `AdviceSpeaker.kt` | `AdviceSpeaker` interface |
| `session/` | `SessionState.kt`, `GameSession.kt` | `SessionState` sealed class, `RoundState` data class, `GameSession` interface |

### 0.4 — Stubs & Mocks (one per module)

These allow compile + test without real implementations:

| Package | File | Behavior |
|---------|------|----------|
| `glasses/` | `MockGlassesConnection.kt` | Emits `Connected` immediately, `hasCameraPermission()` returns `true` |
| `camera/` | `MockFrameProvider.kt` | `startStream()` sets state to `STREAMING`, `captureFrame()` returns a 1x1 blank bitmap |
| `detection/` | `StubCardDetector.kt` | Constructor takes `List<DetectedCard>`, `detect()` returns them every call |
| `counting/` | *(none — HiLoCardCounter is simple enough to be the stub)* | |
| `audio/` | *(none — TtsAdviceSpeaker stub not needed for compile)* | |
| `session/` | *(none — BlackjackSession uses interfaces, tested with stubs above)* | |

### 0.5 — UI Navigation Shell

| Package | File | Contents |
|---------|------|----------|
| `ui/navigation/` | `Screen.kt` | `sealed class Screen` with `Home` and `Session` routes |
| `ui/navigation/` | `AppNavigation.kt` | `NavHost` wiring routes to placeholder composables (will be replaced in Phase 1) |
| `ui/home/` | `HomeUiState.kt` | Data class with `connectionState`, `numDecks`, `canStartSession`, `hasCameraPermission` |
| `ui/session/` | `SessionUiState.kt` | Data class with `sessionState`, `playerCards`, `dealerUpcard`, `recommendedAction`, `runningCount`, `trueCount`, `isAdvising`, `showResetConfirmation` |

### 0.6 — App Shell & DI Skeleton

| File | Contents |
|------|----------|
| `GettingRichApp.kt` | Custom `Application` subclass, calls `ServiceLocator.initialize(this)` |
| `ServiceLocator.kt` | Object with `initialize(context)` and `by lazy` properties for all modules — initially wired to **mocks/stubs** so the app compiles and runs |
| `MainActivity.kt` | Replace scaffold content with `AppNavigation()` |

### 0.7 — Verify

- `./gradlew assembleDebug` succeeds
- App launches, shows placeholder Home screen
- All interfaces are importable from any package

**Commit & push. All developers branch from here.**

---

## Phase 1 — Parallel Module Implementation (all developers simultaneously)

Each developer takes one or more packages. They only depend on `model/` (committed in Phase 0) and their own module interface (also committed in Phase 0). Work is fully independent.

### Developer A — `glasses/` real implementation

| File | Work |
|------|------|
| `glasses/DatGlassesConnection.kt` | Implement using `Wearables.initialize()`, `Wearables.devices` flow, `AutoDeviceSelector`, `RegistrationState` observation. Map DAT states to `ConnectionState` sealed class. |

**Test:** Manual — connect/disconnect with real glasses or `MockDeviceKit`.

### Developer B — `strategy/` (pure logic, no Android deps)

| File | Work |
|------|------|
| `strategy/HandEvaluator.kt` | `evaluate(cards): HandEvaluation` — iterate cards, compute hard/soft totals, detect pairs, detect blackjack |
| `strategy/StrategyTables.kt` | Three 2D `Array<Array<Action>>` constants: `HARD_TABLE[playerTotal][dealerIndex]`, `SOFT_TABLE[softIndex][dealerIndex]`, `PAIR_TABLE[pairIndex][dealerIndex]`. Rules: dealer stands soft 17, DAS allowed, late surrender. |
| `strategy/BasicStrategyEngine.kt` | `recommend()`: check split table if pair, else soft table if soft hand, else hard table. If `DOUBLE` but `handSize > 2`, return `HIT`. If `SURRENDER` but `handSize > 2`, return `HIT`. |

**Test:** JVM unit tests — every cell in all 3 tables, edge cases (blackjack, >2 cards double fallback).

### Developer C — `counting/` + `audio/`

| File | Work |
|------|------|
| `counting/HiLoCardCounter.kt` | Track `runningCount` using `CardValue.hiLoValue`. Per-round dedup via `Set<Card>` — same suit+value in same round = already counted. `nextHand()` clears round set. `resetCount()` resets everything. |
| `audio/TtsAdviceSpeaker.kt` | `initialize()` creates `TextToSpeech` instance. `speak(advice)` builds short utterance string ("Hit", "Stand", "Double down", "Split", "Surrender"; optionally append count). Queue mode = `QUEUE_ADD`. `release()` calls `tts.shutdown()`. |

**Test:** JVM unit tests for counting (sequences, dedup, multi-round, reset). Manual test for TTS on device.

### Developer D — `camera/` + `detection/`

| File | Work |
|------|------|
| `detection/YuvToRgbConverter.kt` | Convert I420 YUV byte array to NV21 (swap U/V planes), then `YuvImage` → `BitmapFactory.decodeByteArray()`. |
| `camera/DatFrameProvider.kt` | `startStream()`: call `Wearables.startStreamSession()`, collect `videoStream` flow, convert each `VideoFrame` via `YuvToRgbConverter`, store latest `FrameData` in `AtomicReference`. `captureFrame()`: return latest stored frame. `stopStream()`: cancel collection, release session. |
| `detection/TfLiteCardDetector.kt` | `initialize()`: load `.tflite` model from assets. `detect()`: resize bitmap to model input size, run inference, apply NMS, map 52 output classes to `Card` objects, filter by confidence threshold. `release()`: close interpreter. |

**Test:** Instrumented test for `TfLiteCardDetector` with labeled card images (target >= 90% accuracy). Unit test for `YuvToRgbConverter` with known byte arrays.

### Developer E — `ui/` screens

| File | Work |
|------|------|
| `ui/home/HomeScreen.kt` | Compose: connection status indicator (color-coded dot + text), deck selector (1–8 spinner, default 6), "Start Session" button (enabled when `canStartSession`). Uses `HomeUiState` directly — no ViewModel dependency yet. Provide `@Preview` with sample states. |
| `ui/session/SessionScreen.kt` | Compose: top status bar (running count, true count), card display area (player cards + dealer upcard), advice text (large, color-coded by action), action buttons (Advise, Next Hand, Reset Count with confirmation dialog, Stop Session). Uses `SessionUiState` directly. Provide `@Preview` with sample states. |

**Test:** Compose previews for each screen state (idle, connected, streaming, advising, advice ready, error).

---

## Phase 2 — Integration (after Phase 1 merges)

### 2.1 — Session Orchestrator (Developer A)

| File | Work |
|------|------|
| `session/BlackjackSession.kt` | Constructor takes all 6 module interfaces. `startSession()`: start frame stream. `advise()`: capture frame → detect → processDetectedCards (dedup via counter) → evaluate hand → recommend → speak. Update `sessionState` and `roundState` flows throughout. `nextHand()`: delegate to counter. `resetCount()`: delegate to counter. `stopSession()`: stop stream, release resources. |

### 2.2 — ViewModels (Developer B)

| File | Work |
|------|------|
| `ui/home/HomeViewModel.kt` | Constructor takes `GlassesConnection`, `CardCounter`. Collect `connectionState` flow. Expose `HomeUiState` as `StateFlow`. Methods: `onNumDecksChanged()`, `onStartSession()`. Companion `Factory` pulls from `ServiceLocator`. |
| `ui/session/SessionViewModel.kt` | Constructor takes `GameSession`, `GlassesConnection`, `CardCounter`. Combine `sessionState`, `roundState`, `countState` into `SessionUiState`. Methods: `onAdvise()`, `onNextHand()`, `onResetCountConfirmed()`, `onStopSession()`. Companion `Factory` pulls from `ServiceLocator`. |

### 2.3 — Wire DI to Real Implementations (Developer A or B)

| File | Change |
|------|--------|
| `ServiceLocator.kt` | Replace mock/stub bindings with real implementations (`DatGlassesConnection`, `DatFrameProvider`, `TfLiteCardDetector`, `HiLoCardCounter`, `BasicStrategyEngine`, `TtsAdviceSpeaker`, `BlackjackSession`) |
| `GettingRichApp.kt` | Already calls `ServiceLocator.initialize(this)` — no change needed |
| `MainActivity.kt` | Register Bluetooth permission launcher, pass `Activity` reference for glasses registration if needed |

### 2.4 — Wire ViewModels into Screens (Developer E)

| File | Change |
|------|--------|
| `HomeScreen.kt` | Accept `HomeViewModel` parameter, call `viewModel(factory = HomeViewModel.Factory)`, collect `uiState` |
| `SessionScreen.kt` | Accept `SessionViewModel` parameter, call `viewModel(factory = SessionViewModel.Factory)`, collect `uiState`, wire button callbacks |
| `AppNavigation.kt` | Instantiate ViewModels in `NavHost` composable blocks, pass to screens |

---

## Phase 3 — Testing & Polish (parallel, after Phase 2)

### Unit Tests (JVM)

| Developer | Test File | Coverage |
|-----------|-----------|----------|
| **B** | `HandEvaluatorTest.kt` | Hard totals, soft totals (ace handling), pairs, blackjack detection |
| **B** | `BasicStrategyEngineTest.kt` | Every cell in hard/soft/pair tables, double fallback (>2 cards), surrender fallback |
| **C** | `HiLoCardCounterTest.kt` | Running count sequences, per-round dedup, `nextHand()` clears round, `resetCount()` full reset, true count calculation |

### Integration Tests (JVM)

| Developer | Test File | Coverage |
|-----------|-----------|----------|
| **A** | `BlackjackSessionTest.kt` | End-to-end with `StubCardDetector` + `MockFrameProvider`: multi-round scenario, verify correct advice and count at each step |

### Instrumented Tests (Android)

| Developer | Test File | Coverage |
|-----------|-----------|----------|
| **D** | `TfLiteCardDetectorTest.kt` | Load model, run inference on labeled test images, assert >= 90% accuracy |
| **E** | Compose screenshot tests | HomeScreen + SessionScreen in all states |

### Manual Smoke Test

| Step | Verify |
|------|--------|
| 1. Build & install APK | `assembleDebug` succeeds |
| 2. Launch app | Home screen renders, shows `Disconnected` |
| 3. Connect glasses (or mock device) | Status changes to `Connected` |
| 4. Set deck count, tap Start Session | Navigates to Session screen, stream starts |
| 5. Tap Advise | Cards detected, advice displayed and spoken |
| 6. Tap Next Hand | Round clears, count persists |
| 7. Tap Reset Count (confirm) | Count resets to 0 |
| 8. Tap Stop Session | Returns to Home screen |

---

## File Checklist by Phase

### Phase 0 (Foundation) — 23 files

```
Modified:
  gradle/libs.versions.toml
  settings.gradle.kts
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/example/gettingrichapp/MainActivity.kt

New:
  app/src/main/java/com/example/gettingrichapp/model/Card.kt
  app/src/main/java/com/example/gettingrichapp/model/HandEvaluation.kt
  app/src/main/java/com/example/gettingrichapp/model/Advice.kt
  app/src/main/java/com/example/gettingrichapp/glasses/ConnectionState.kt
  app/src/main/java/com/example/gettingrichapp/glasses/GlassesConnection.kt
  app/src/main/java/com/example/gettingrichapp/glasses/MockGlassesConnection.kt
  app/src/main/java/com/example/gettingrichapp/camera/FrameData.kt
  app/src/main/java/com/example/gettingrichapp/camera/FrameProvider.kt
  app/src/main/java/com/example/gettingrichapp/camera/MockFrameProvider.kt
  app/src/main/java/com/example/gettingrichapp/detection/DetectionResult.kt
  app/src/main/java/com/example/gettingrichapp/detection/CardDetector.kt
  app/src/main/java/com/example/gettingrichapp/detection/StubCardDetector.kt
  app/src/main/java/com/example/gettingrichapp/counting/CountState.kt
  app/src/main/java/com/example/gettingrichapp/counting/CardCounter.kt
  app/src/main/java/com/example/gettingrichapp/strategy/StrategyEngine.kt
  app/src/main/java/com/example/gettingrichapp/audio/AdviceSpeaker.kt
  app/src/main/java/com/example/gettingrichapp/session/SessionState.kt
  app/src/main/java/com/example/gettingrichapp/session/GameSession.kt
  app/src/main/java/com/example/gettingrichapp/ui/navigation/Screen.kt
  app/src/main/java/com/example/gettingrichapp/ui/navigation/AppNavigation.kt
  app/src/main/java/com/example/gettingrichapp/ui/home/HomeUiState.kt
  app/src/main/java/com/example/gettingrichapp/ui/session/SessionUiState.kt
  app/src/main/java/com/example/gettingrichapp/GettingRichApp.kt
  app/src/main/java/com/example/gettingrichapp/ServiceLocator.kt
```

### Phase 1 (Parallel Implementation) — 10 files

```
Developer A:
  app/src/main/java/com/example/gettingrichapp/glasses/DatGlassesConnection.kt

Developer B:
  app/src/main/java/com/example/gettingrichapp/strategy/HandEvaluator.kt
  app/src/main/java/com/example/gettingrichapp/strategy/StrategyTables.kt
  app/src/main/java/com/example/gettingrichapp/strategy/BasicStrategyEngine.kt

Developer C:
  app/src/main/java/com/example/gettingrichapp/counting/HiLoCardCounter.kt
  app/src/main/java/com/example/gettingrichapp/audio/TtsAdviceSpeaker.kt

Developer D:
  app/src/main/java/com/example/gettingrichapp/detection/YuvToRgbConverter.kt
  app/src/main/java/com/example/gettingrichapp/camera/DatFrameProvider.kt
  app/src/main/java/com/example/gettingrichapp/detection/TfLiteCardDetector.kt

Developer E:
  app/src/main/java/com/example/gettingrichapp/ui/home/HomeScreen.kt
  app/src/main/java/com/example/gettingrichapp/ui/session/SessionScreen.kt
```

### Phase 2 (Integration) — 5 new files + 4 modified

```
New:
  app/src/main/java/com/example/gettingrichapp/session/BlackjackSession.kt
  app/src/main/java/com/example/gettingrichapp/ui/home/HomeViewModel.kt
  app/src/main/java/com/example/gettingrichapp/ui/session/SessionViewModel.kt

Modified:
  app/src/main/java/com/example/gettingrichapp/ServiceLocator.kt
  app/src/main/java/com/example/gettingrichapp/MainActivity.kt
  app/src/main/java/com/example/gettingrichapp/ui/home/HomeScreen.kt
  app/src/main/java/com/example/gettingrichapp/ui/session/SessionScreen.kt
  app/src/main/java/com/example/gettingrichapp/ui/navigation/AppNavigation.kt
```

### Phase 3 (Testing) — test files

```
app/src/test/java/com/example/gettingrichapp/strategy/HandEvaluatorTest.kt
app/src/test/java/com/example/gettingrichapp/strategy/BasicStrategyEngineTest.kt
app/src/test/java/com/example/gettingrichapp/counting/HiLoCardCounterTest.kt
app/src/test/java/com/example/gettingrichapp/session/BlackjackSessionTest.kt
app/src/androidTest/java/com/example/gettingrichapp/detection/TfLiteCardDetectorTest.kt
```

---

## Dependency Graph (what blocks what)

```
Phase 0 ──────────────────────────────────────────────────►
    │
    ├── Dev A: glasses/DatGlassesConnection ──────────────┐
    ├── Dev B: strategy/* ────────────────────────────────┐│
    ├── Dev C: counting/HiLoCardCounter + audio/Tts ─────┐││
    ├── Dev D: camera/Dat + detection/TfLite + YuvConv ──┐│││
    └── Dev E: ui/HomeScreen + ui/SessionScreen ─────────┐││││
                                                         │││││
                                                         ▼▼▼▼▼
                                                     Phase 2: Integration
                                                         │
                                                         ▼
                                                     Phase 3: Testing
```

**Critical path:** Phase 0 → any Phase 1 track → Phase 2 integration → Phase 3 testing.

No Phase 1 developer blocks any other Phase 1 developer. Phase 2 cannot start until all Phase 1 work for the required modules is merged.
