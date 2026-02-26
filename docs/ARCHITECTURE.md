# Card Game Advisor — Modular Architecture Plan

## Context

The project is a greenfield Android app (`GettingRichApp`) that pairs with Meta Ray-Ban smart glasses via the DAT SDK. It receives a camera stream, detects playing cards via on-device ML, and provides real-time blackjack advice (basic strategy + Hi-Lo counting) as audio through the glasses speaker. The current repo is a default Android Studio scaffold with a single `MainActivity` and Compose theme.

The goal is to establish a **parallelizable, interface-driven package architecture** within the single `app` module so that 4–5 developers can work on independent modules simultaneously — glasses connectivity, card detection ML, strategy engine, counting engine, UI — without blocking each other.
DAT SDK overview: <https://wearables.developer.meta.com/docs/develop>
DAT SDK API Ref: <https://wearables.developer.meta.com/docs/reference/android/dat/0.4>

---

## Architecture Overview

**Dependency flow (top to bottom, no reverse deps):**

```
               model/          ← shared data types (zero deps)
                 │
   ┌─────────┬───┼────┬──────────┬──────────┐
glasses/  camera/  detection/  counting/  strategy/  audio/
 (intf)   (intf)    (intf)      (intf)     (intf)    (intf)
   └─────────┴───┬────┴──────────┴──────────┘
                 │
             session/           ← BlackjackSession orchestrates all modules
                 │
            ViewModels          ← thin UI-state adapters
                 │
               UI               ← Compose screens + navigation
```

Every domain module is an **interface + implementation + test stub**, so developers code against contracts and test with fakes.

---

## Package & File Breakdown (36 new files, 3 modified, 4 build config)

### `model/` — Shared data types (no dependencies)

| File | Contents |
|------|----------|
| `Card.kt` | `Card`, `CardValue` enum (with `numericValue`, `hiLoValue`), `Suit` enum |
| `HandEvaluation.kt` | `HandEvaluation` data class |
| `Advice.kt` | `Action` enum, `Advice` data class |

### `glasses/` — DAT SDK connection wrapper

| File | Contents |
|------|----------|
| `ConnectionState.kt` | Sealed class: `Disconnected`, `Searching`, `Connected`, `Error` |
| `GlassesConnection.kt` | Interface: `connectionState: StateFlow`, `initialize()`, `startRegistration()`, `hasCameraPermission()`, `release()` |
| `DatGlassesConnection.kt` | Real impl using `Wearables.*`, `AutoDeviceSelector`, `RegistrationState` flows |
| `MockGlassesConnection.kt` | Emits `Connected` immediately for dev/test |

### `camera/` — Frame capture abstraction

| File | Contents |
|------|----------|
| `FrameData.kt` | Data class: `bitmap: Bitmap`, `timestampMs`, `width`, `height` |
| `FrameProvider.kt` | Interface + `StreamState` enum: `startStream()`, `captureFrame(): FrameData?`, `stopStream()` |
| `DatFrameProvider.kt` | Real impl: `Wearables.startStreamSession()`, I420→NV21→Bitmap conversion, holds latest frame |
| `MockFrameProvider.kt` | Returns a static test bitmap |

### `detection/` — Card detection ML

| File | Contents |
|------|----------|
| `DetectionResult.kt` | `DetectedCard` (card, confidence, boundingBox), `DetectionResult` |
| `CardDetector.kt` | Interface: `initialize()`, `detect(bitmap, threshold): DetectionResult`, `release()` |
| `TfLiteCardDetector.kt` | TFLite YOLOv8-nano: load model from assets, preprocess, inference, NMS, map 52 classes to `Card` |
| `StubCardDetector.kt` | Returns pre-configured cards for testing |
| `YuvToRgbConverter.kt` | I420 YUV → NV21 → Bitmap helper (used by `DatFrameProvider`) |

### `counting/` — Hi-Lo card counting

| File | Contents |
|------|----------|
| `CountState.kt` | Data class: `runningCount`, `totalCardsSeen`, `numDecks`, `cardsSeenThisRound`, computed `trueCount` |
| `CardCounter.kt` | Interface: `countState: StateFlow`, `setNumDecks()`, `processDetectedCards(): List<Card>` (dedup + count), `nextHand()`, `resetCount()` |
| `HiLoCardCounter.kt` | Implementation with per-round deduplication (same value+suit = same card within a round) |

### `strategy/` — Blackjack basic strategy

| File | Contents |
|------|----------|
| `HandEvaluator.kt` | Class: `evaluate(cards): HandEvaluation` — computes hard/soft totals, isPair, isBlackjack |
| `StrategyEngine.kt` | Interface: `recommend(playerHand, dealerUpcard, handSize): Action` |
| `BasicStrategyEngine.kt` | Lookup table impl with double-down (>2 cards → HIT) and split (non-pair → hard/soft) fallbacks |
| `StrategyTables.kt` | Constant 2D arrays: hard totals (5–20 × 2–A), soft totals (A2–A9 × 2–A), pairs (AA–TT × 2–A). Assumes: dealer stands soft 17, DAS allowed, late surrender, 3:2 BJ |

### `audio/` — TTS advice delivery

| File | Contents |
|------|----------|
| `AdviceSpeaker.kt` | Interface: `initialize()`, `speak(advice)`, `includeCountInSpeech`, `release()` |
| `TtsAdviceSpeaker.kt` | Android `TextToSpeech` impl, queues utterances (no interrupt), short phrases ("Hit", "Stand", etc.) |

### `session/` — Game session orchestrator

| File | Contents |
|------|----------|
| `SessionState.kt` | `SessionState` sealed class (`Idle`, `Streaming`, `Analyzing`, `AdviceReady`, `Error`) + `RoundState` data class |
| `GameSession.kt` | Interface: `sessionState: StateFlow`, `roundState: StateFlow`, `startSession()`, `advise()`, `nextHand()`, `resetCount()`, `stopSession()` |
| `BlackjackSession.kt` | **Core orchestrator**: `advise()` = captureFrame → detect → processDetectedCards (dedup) → evaluate hand → recommend action → speak. Depends on all 6 module interfaces |

### `ui/navigation/`

| File | Contents |
|------|----------|
| `Screen.kt` | Sealed class routes: `Home`, `Session` |
| `AppNavigation.kt` | `NavHost` composable wiring routes to screens |

### `ui/home/`

| File | Contents |
|------|----------|
| `HomeUiState.kt` | `connectionState`, `numDecks`, `canStartSession`, `hasCameraPermission` |
| `HomeViewModel.kt` | Observes `GlassesConnection.connectionState`, manages deck config, checks camera permission |
| `HomeScreen.kt` | Compose: connection indicator, deck selector (1–8, default 6), "Start Session" button |

### `ui/session/`

| File | Contents |
|------|----------|
| `SessionUiState.kt` | `sessionState`, `playerCards`, `dealerUpcard`, `recommendedAction`, `runningCount`, `trueCount`, `isAdvising`, `showResetConfirmation` |
| `SessionViewModel.kt` | Combines flows from `GameSession`, `GlassesConnection`, `CardCounter`. Methods: `onAdvise()`, `onNextHand()`, `onResetCountConfirmed()`, `onStopSession()` |
| `SessionScreen.kt` | Compose: status bar (counts), card display area, advice text, buttons (Advise, Next Hand, Reset Count with confirm dialog, Stop Session) |

### Root-level files

| File | Contents |
|------|----------|
| `GettingRichApp.kt` | Custom `Application` class, calls `ServiceLocator.initialize(this)` in `onCreate()` |
| `ServiceLocator.kt` | Object with `by lazy` singletons for all module implementations + ViewModel factories |

---

## Module Interfaces

### GlassesConnection

```kotlin
interface GlassesConnection {
    val connectionState: StateFlow<ConnectionState>
    suspend fun initialize()
    fun startRegistration(activity: Activity)
    fun startUnregistration(activity: Activity)
    suspend fun hasCameraPermission(): Boolean
    fun cameraPermissionContract(): Any
    fun release()
}
```

### FrameProvider

```kotlin
enum class StreamState { IDLE, STREAMING, ERROR }

interface FrameProvider {
    val streamState: StateFlow<StreamState>
    suspend fun startStream()
    suspend fun captureFrame(): FrameData?
    suspend fun stopStream()
}
```

### CardDetector

```kotlin
interface CardDetector {
    fun initialize()
    suspend fun detect(frame: Bitmap, confidenceThreshold: Float = 0.80f): DetectionResult
    fun release()
}
```

### CardCounter

```kotlin
interface CardCounter {
    val countState: StateFlow<CountState>
    fun setNumDecks(numDecks: Int)
    fun processDetectedCards(detectedCards: List<Card>): List<Card>
    fun nextHand()
    fun resetCount()
}
```

### StrategyEngine

```kotlin
interface StrategyEngine {
    fun recommend(playerHand: HandEvaluation, dealerUpcard: Card, handSize: Int): Action
}
```

### AdviceSpeaker

```kotlin
interface AdviceSpeaker {
    fun initialize()
    fun speak(advice: Advice)
    var includeCountInSpeech: Boolean
    fun release()
}
```

### GameSession

```kotlin
interface GameSession {
    val sessionState: StateFlow<SessionState>
    val roundState: StateFlow<RoundState>
    suspend fun startSession()
    suspend fun advise()
    fun nextHand()
    fun resetCount()
    suspend fun stopSession()
}
```

---

## Files to Modify

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Bump `minSdk` to 29. Add deps: navigation-compose, lifecycle-viewmodel-compose, coroutines, tflite, tflite-support, dat-core, dat-camera, dat-mockdevice (debug only) |
| `gradle/libs.versions.toml` | Add version catalog entries for all new deps |
| `settings.gradle.kts` | Add GitHub Packages Maven repo for DAT SDK artifacts (requires `GITHUB_TOKEN`) |
| `AndroidManifest.xml` | Add `android:name=".GettingRichApp"`, add `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `INTERNET` permissions, add DAT `APPLICATION_ID` meta-data |
| `MainActivity.kt` | Replace scaffold content with `AppNavigation()` composable, register permission launcher |

---

## DI Approach — ServiceLocator (no Hilt)

```kotlin
object ServiceLocator {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val glassesConnection: GlassesConnection by lazy { DatGlassesConnection(appContext) }
    val frameProvider: FrameProvider by lazy { DatFrameProvider(appContext, glassesConnection) }
    val cardDetector: CardDetector by lazy { TfLiteCardDetector(appContext).also { it.initialize() } }
    val cardCounter: CardCounter by lazy { HiLoCardCounter() }
    val handEvaluator: HandEvaluator by lazy { HandEvaluator() }
    val strategyEngine: StrategyEngine by lazy { BasicStrategyEngine() }
    val adviceSpeaker: AdviceSpeaker by lazy { TtsAdviceSpeaker(appContext).also { it.initialize() } }
    val gameSession: GameSession by lazy {
        BlackjackSession(
            frameProvider = frameProvider,
            cardDetector = cardDetector,
            cardCounter = cardCounter,
            handEvaluator = handEvaluator,
            strategyEngine = strategyEngine,
            adviceSpeaker = adviceSpeaker
        )
    }
}
```

ViewModels use companion object factories that pull from `ServiceLocator`:

```kotlin
companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                glassesConnection = ServiceLocator.glassesConnection,
                cardCounter = ServiceLocator.cardCounter
            )
        }
    }
}
```

---

## Parallelization Plan

### Phase 1 — All developers start simultaneously (after `model/` is committed)

| Developer | Package(s) | Files | Blocking dependency |
|-----------|-----------|-------|---------------------|
| **A** | `model/` → `glasses/` | 7 files | None — model first (commit), then glasses |
| **B** | `strategy/` | 4 files | Only `model/` |
| **C** | `counting/` + `audio/` | 5 files | Only `model/` |
| **D** | `camera/` + `detection/` | 7 files | Only `model/` |
| **E** | `ui/` (all screens + nav) | 6 files | Only `model/` — uses preview data / stub states |

### Phase 2 — Integration (once interfaces exist)

| Developer | Task | Files |
|-----------|------|-------|
| **A** | Session orchestrator | `session/` (3 files) |
| **B** | ViewModels | `HomeViewModel.kt`, `SessionViewModel.kt` |
| **A or B** | DI + wiring | `ServiceLocator.kt`, `GettingRichApp.kt`, `MainActivity.kt` |
| **E** | Wire real ViewModels into screens | Update `HomeScreen.kt`, `SessionScreen.kt` |
| **D** | TFLite model integration | `TfLiteCardDetector.kt` + model asset |

### Phase 3 — Testing (parallel)

| Developer | Tests | Isolation? |
|-----------|-------|-----------|
| **B** | Strategy tables: every cell in hard/soft/pair tables | Yes — pure functions |
| **C** | Counting: card sequences, dedup, next hand, reset | Yes — pure logic |
| **A** | BlackjackSession end-to-end with `StubCardDetector` + `MockFrameProvider` | Yes — all deps are interfaces |
| **D** | TfLiteCardDetector with labeled test images | Android instrumented test |
| **E** | Compose UI previews + screenshot tests | Compose test deps |

---

## Key Design Decisions

1. **Package-level modularity, not Gradle modules** — avoids build complexity for an MVP with ~6 logical modules. Interface boundaries provide the same developer isolation.

2. **`GameSession` orchestrator separate from ViewModel** — the advise pipeline (capture → detect → dedup → count → evaluate → recommend → speak) is business logic that should be testable without any Android ViewModel machinery. `BlackjackSession` coordinates the pipeline and is unit-testable with all-fake dependencies. The ViewModel is a thin UI-state adapter.

3. **Deduplication lives in `CardCounter`, not `CardDetector`** — dedup is a counting concern ("have I already counted this card this round?"). The detector reports everything it sees; the counter decides what is new.

4. **`FrameProvider` abstracts YUV conversion** — the `CardDetector` receives clean `Bitmap`, never touches I420 format. All DAT SDK video frame conversion complexity is isolated in `DatFrameProvider` + `YuvToRgbConverter`.

5. **Manual DI via `ServiceLocator`** — simplest correct pattern for an MVP with ~8 dependencies. Replaceable with Hilt later by adding `@Inject` annotations.

---

## Verification Plan

1. **Unit tests (JVM, no Android):** `HandEvaluator`, `BasicStrategyEngine` (all 3 tables), `HiLoCardCounter` (sequences, dedup, reset)
2. **Integration test with stubs:** `BlackjackSession` with `StubCardDetector` + `MockFrameProvider` — feed a multi-round scenario, assert correct advice and count at each step
3. **Instrumented test:** `TfLiteCardDetector` with labeled card images — target ≥90% accuracy
4. **DAT mock test:** Use `MockDeviceKit` + `MockCameraKit.setCameraFeed(uri)` with pre-recorded video of cards — end-to-end from stream to advice
5. **Compose previews:** Every screen has `@Preview` with representative `UiState` values (idle, connected, advising, advice ready, error)
6. **Manual smoke test:** Build → connect to real glasses (or mock device) → start session → advise → verify audio output + on-screen display
