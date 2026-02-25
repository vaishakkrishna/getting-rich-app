# Card Game Advisor — Product Spec

## Overview

An Android app that pairs with Meta Ray-Ban smart glasses via the DAT SDK. The app receives a camera stream from the glasses, detects playing cards in frame, and provides real-time blackjack advice (basic strategy + card counting) delivered as audio through the glasses speaker.

---

## References

- **DAT SDK (Android):** https://github.com/facebook/meta-wearables-dat-android
- **DAT Docs:** https://wearables.developer.meta.com/llms.txt?full=true
- **SDK version:** 0.4.0 (developer preview)

### DAT SDK Capabilities (relevant subset)

| Capability | Detail |
|---|---|
| Camera stream | Configurable resolution (720×1280 / 504×896 / 360×640), 2–30 FPS, H.265 or raw codec |
| Photo capture | JPEG / RAW single frames |
| Audio output | A2DP (media) and HFP (voice) — audio played from the Android app routes to glasses automatically when connected via Bluetooth |
| Core libraries | `mwdat-core` (connectivity), `mwdat-camera` (video/photo), `mwdat-mockdevice` (testing) |

> **Note:** The DAT SDK provides the camera stream and audio output channel. It does **not** include card detection / computer vision. Card recognition must be implemented separately (see Technical Architecture § Card Detection).

---

## MVP Scope

### In Scope
- Blackjack only (single game mode)
- Card detection from glasses camera stream
- Hi-Lo card counting (running count + true count)
- Basic strategy advice (hard totals, soft totals, pair splitting, doubling down)
- Audio delivery of advice through glasses speaker
- Manual "deck reset" for when the shoe is shuffled
- Configurable number of decks (1–8)

### Out of Scope (future)
- Other card games (poker, etc.)
- Automatic shuffle detection
- Bet sizing recommendations
- Dealer upcard detection from across the table (MVP assumes user can point glasses at dealer's upcard)
- iOS support

---

## User Flow

### Setup (one-time)
1. User pairs Meta Ray-Ban glasses with their Android phone via Bluetooth.
2. User opens the Card Game Advisor app.
3. App connects to glasses via DAT SDK (`mwdat-core` device discovery).
4. User configures settings: number of decks in the shoe (default: 6).

### Gameplay Loop
1. User taps **"Start Session"** — app begins the DAT camera stream.
2. Dealer deals cards. User looks at their hand and the dealer's upcard through the glasses.
3. User taps **"Advise"** in the app.
   - App captures the current camera frame(s).
   - App runs card detection to identify all visible cards.
   - App updates the card counting state with any newly-detected cards.
   - App calculates the optimal play using basic strategy (factoring in the player's hand, dealer upcard, and current count).
   - App speaks the advice through the glasses via audio output (e.g., *"Hit"*, *"Stand"*, *"Double down"*, *"Split"*).
   - App displays the advice, detected cards, and current running count on the phone screen.
4. If the user taps **"Advise"** again in the same round (e.g., after a hit):
   - App captures a new frame, detects cards, and identifies which cards are **new** vs. already tracked this round.
   - Only new cards update the count.
   - App recalculates and delivers updated advice.
5. User taps **"Next Hand"** to signal the round is over. The per-round hand tracking resets, but the running count persists.
6. User taps **"Reset Count"** when the shoe is shuffled. Running count resets to 0.

### State Diagram

```
[Idle] --Start Session--> [Streaming]
[Streaming] --Advise--> [Analyzing] --result--> [Streaming] (advice displayed/spoken)
[Streaming] --Next Hand--> [Streaming] (round state cleared, count preserved)
[Streaming] --Reset Count--> [Streaming] (count zeroed)
[Streaming] --Stop Session--> [Idle]
```

---

## Screen Specifications

### Screen 1: Home / Setup
- Game selector (disabled, shows "Blackjack" — only option for MVP)
- Number of decks selector: stepper or dropdown, values 1–8, default 6
- Glasses connection status indicator (connected / disconnected / searching)
- **"Start Session"** button (enabled only when glasses are connected)

### Screen 2: Session (main gameplay screen)
- **Status bar:** connection status, running count, true count (running count ÷ estimated remaining decks)
- **Card display area:** shows detected cards for the current round, grouped as "Your Hand" and "Dealer Upcard"
- **Advice display:** large text showing the recommended action (Hit / Stand / Double Down / Split / Surrender)
- **Buttons:**
  - **"Advise"** — primary action, large and prominent
  - **"Next Hand"** — secondary action
  - **"Reset Count"** — destructive action, requires confirmation dialog ("Are you sure? This resets the running count to 0.")
  - **"Stop Session"** — ends streaming, returns to Home

---

## Technical Architecture

### Stack
- **Language:** Kotlin
- **Min SDK:** Android 10 (API 29) — required by DAT SDK
- **UI:** Jetpack Compose
- **DAT SDK:** `mwdat-core` 0.4.0, `mwdat-camera` 0.4.0, `mwdat-mockdevice` 0.4.0 (for testing)

### Component Breakdown

#### 1. DAT Integration Layer
**Responsibility:** Manage glasses connection, camera streaming, and audio output.

- Use `mwdat-core` for device discovery and connection management.
- Use `mwdat-camera` to configure and start a `StreamSession`.
- Recommended stream config for card detection: **medium resolution (504×896), 15 FPS, H.265 codec** — balances quality and battery.
- On "Advise" tap: capture the latest frame(s) from the stream and pass to the Card Detection module.
- For audio output: use standard Android `MediaPlayer` or `TextToSpeech` API. Audio routes to glasses automatically via active Bluetooth A2DP connection.

#### 2. Card Detection Module
**Responsibility:** Identify playing cards (value + suit) from a camera frame.

The DAT SDK does **not** provide card recognition. Options (ordered by recommendation):

| Approach | Pros | Cons |
|---|---|---|
| **A. On-device ML model (TFLite / ONNX)** | Low latency, works offline, no per-request cost | Requires training data, model size (~5–20 MB) |
| **B. Cloud vision API (Google Cloud Vision, AWS Rekognition, or custom endpoint)** | Easier to iterate on accuracy | Requires network, adds latency, ongoing cost |
| **C. On-device classical CV (OpenCV contour detection + template matching)** | No ML training needed, fully offline | Brittle with varied lighting/angles, harder to maintain |

**Recommended for MVP: Approach A (on-device ML).** Use a pre-trained or fine-tuned object detection model (e.g., YOLOv8-nano exported to TFLite). The model should detect and classify 52 card classes (e.g., `2_hearts`, `ace_spades`, etc.).

**Input:** Bitmap frame from DAT camera stream.
**Output:** List of detected cards, each with: `value` (2–10, J, Q, K, A), `suit` (hearts, diamonds, clubs, spades), `confidence` score, `boundingBox`.

**Acceptance criteria:**
- Detect cards held ~12–18 inches from glasses (typical hand distance).
- Minimum confidence threshold: 0.80 (configurable).
- Must handle partial occlusion (overlapping cards in a hand).

#### 3. Card Counting Engine
**Responsibility:** Maintain a running count using the Hi-Lo system.

**Hi-Lo values:**
| Cards | Count Value |
|---|---|
| 2, 3, 4, 5, 6 | +1 |
| 7, 8, 9 | 0 |
| 10, J, Q, K, A | −1 |

**State:**
- `runningCount: Int` — sum of Hi-Lo values for all cards seen since last reset.
- `cardsSeenThisSession: Set<CardInstance>` — prevents double-counting a card detected in multiple frames. A `CardInstance` is identified by its value + suit + a sequence ID assigned per round.
- `cardsSeenThisRound: List<Card>` — cards detected during the current round (cleared on "Next Hand").
- `totalCardsSeen: Int` — used to estimate remaining decks for true count.
- `numDecks: Int` — configured by user.

**True count** = `runningCount / estimatedRemainingDecks`
where `estimatedRemainingDecks = (numDecks * 52 - totalCardsSeen) / 52.0`

**"Next Hand"** clears `cardsSeenThisRound` but preserves `runningCount`, `cardsSeenThisSession`, and `totalCardsSeen`.
**"Reset Count"** clears everything.

#### 4. Basic Strategy Engine
**Responsibility:** Given the player's hand, dealer's upcard, and optionally the true count, return the optimal action.

**Actions:** `HIT`, `STAND`, `DOUBLE_DOWN`, `SPLIT`, `SURRENDER`

The engine should implement standard basic strategy lookup tables for:

1. **Hard totals** (no ace, or ace counted as 1): player total (5–20) × dealer upcard (2–A) → action
2. **Soft totals** (ace counted as 11): player total (A-2 through A-9) × dealer upcard (2–A) → action
3. **Pairs**: pair value (A-A through 10-10) × dealer upcard (2–A) → action

Strategy tables should assume:
- Dealer stands on soft 17
- Double after split allowed
- Late surrender allowed
- Blackjack pays 3:2

These assumptions should be configurable in a future iteration but hardcoded for MVP.

**Implementation:** Encode the three strategy tables as 2D arrays or maps. No complex logic needed — this is pure table lookup.

**Fallback rules for `DOUBLE_DOWN` and `SPLIT`:**
- If the engine recommends `DOUBLE_DOWN` but the hand has more than 2 cards (already hit), fall back to `HIT`.
- If the engine recommends `SPLIT` but the hand is not a pair, fall back to the hard/soft total lookup.

#### 5. Audio Advice Delivery
**Responsibility:** Speak the recommended action through the glasses.

- Use Android `TextToSpeech` API.
- Keep utterances short and clear: *"Hit"*, *"Stand"*, *"Double down"*, *"Split aces"*, *"Surrender"*.
- Do not interrupt a currently-playing utterance. Queue if needed.
- Optionally prefix with count info at configurable verbosity (e.g., *"Count plus 4. Hit."*). Default: action only.

#### 6. Deduplication Logic (same-round re-advise)
**Responsibility:** When "Advise" is tapped multiple times in one round, avoid double-counting previously seen cards.

- On each "Advise" tap, detect all cards in frame.
- Compare detected cards against `cardsSeenThisRound`.
- Only cards **not** already in `cardsSeenThisRound` are new — add them to the round list and update the running count.
- Recalculate player hand total and re-run the strategy engine with the full current hand.

**Edge case:** If the same card (e.g., 7 of spades) appears twice in a multi-deck shoe, the deduplication should compare by position/context within the round, not just value+suit. For MVP, assume the same value+suit seen in the same round is the same physical card (acceptable simplification for ≤8 deck shoes where duplicates in one hand are rare).

---

## Data Model

```kotlin
data class Card(
    val value: CardValue,  // TWO, THREE, ..., TEN, JACK, QUEEN, KING, ACE
    val suit: Suit         // HEARTS, DIAMONDS, CLUBS, SPADES
)

enum class CardValue(val numericValue: Int, val hiLoValue: Int) {
    TWO(2, 1), THREE(3, 1), FOUR(4, 1), FIVE(5, 1), SIX(6, 1),
    SEVEN(7, 0), EIGHT(8, 0), NINE(9, 0),
    TEN(10, -1), JACK(10, -1), QUEEN(10, -1), KING(10, -1),
    ACE(11, -1)  // 11 or 1, handled by hand evaluation logic
}

enum class Suit { HEARTS, DIAMONDS, CLUBS, SPADES }

enum class Action { HIT, STAND, DOUBLE_DOWN, SPLIT, SURRENDER }

data class HandEvaluation(
    val cards: List<Card>,
    val hardTotal: Int,
    val softTotal: Int?,      // null if no ace or if soft total > 21
    val isPair: Boolean,
    val isSoft: Boolean,
    val isBlackjack: Boolean
)

data class Advice(
    val action: Action,
    val playerHand: HandEvaluation,
    val dealerUpcard: Card,
    val runningCount: Int,
    val trueCount: Double
)
```

---

## Testing Strategy

- **Card Detection:** Use `mwdat-mockdevice` to feed pre-recorded frames with known cards. Validate detection accuracy against labeled test set (target: ≥90% accuracy on well-lit, standard-sized cards).
- **Counting Engine:** Unit tests with known sequences of cards — verify running count and true count against hand-calculated expected values.
- **Strategy Engine:** Unit tests covering every cell in all three strategy tables (hard, soft, pairs). Compare output against a published basic strategy chart.
- **Deduplication:** Unit tests for same-round re-advise scenarios — ensure no double-counting.
- **Integration:** End-to-end test using mock device: feed a multi-round blackjack scenario, verify correct advice and count at each step.

---

## Open Questions

1. **Card detection model:** Do we train our own model, or is there an off-the-shelf playing card detection model we can fine-tune? Engineering to research during spike.
2. **Camera frame capture vs. continuous detection:** Should "Advise" capture a single frame, or should it analyze a short burst (e.g., 5 frames over 1 second) and take the highest-confidence detections? Burst is more reliable but adds latency.
3. **Dealer upcard detection reliability:** The dealer's upcard is farther away and at a different angle. Do we need the user to explicitly look at it, or can we detect it from the same frame as the player's hand?
4. **Multiple players at the table:** Should the app count cards from other players' hands if visible? This would improve count accuracy but adds detection complexity. Recommend: out of scope for MVP, revisit post-launch.
