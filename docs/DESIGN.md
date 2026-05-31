# PokeRarityScanner — Design Audit

> **Date:** 2026-05-31 · **Scope:** All user-facing screens and components  
> **Target aesthetic:** Clean, premium, mobile-first dark UI — Pokémon fan-tool feel without being childish.

---

## 1 · Screen & Component Inventory

### 1.1 Screens

| Screen | File(s) | Tech | Notes |
|---|---|---|---|
| **Splash** | `SplashActivity` + `activity_splash.xml` | View/XML | Pokéball scale-up → progress bar → "Initializing…" |
| **Main / Collection** | `MainActivity` → `CollectionScreen` (Compose) | Compose | Hero stat block, bento grid, FAB, filter chips, list |
| **Scan Result (in-app)** | `ScanResultScreen` → `ScanResultOverlayCard` | Compose | Type-gradient background + overlay card |
| **Scan Result (standalone)** | `ResultActivity` → `ScanResultScreen` | Compose (via Activity) | Same card, separate entry point |
| **History** | `HistoryActivity` + `activity_history.xml` + `item_scan_history.xml` | View/XML + RecyclerView | Material Toolbar + Chip filter + RV |
| **Overlay (floating)** | `ScanResultOverlayCard` + `OverlayComponents` | Compose (WindowManager) | Drawn on top of PoGO |
| **Dialogs** | `TelemetryConsentDialog`, `TelemetrySettingsDialog` | Compose `AlertDialog` | Standard M3 AlertDialog |

### 1.2 Shared Components

| Component | Location | Purpose |
|---|---|---|
| `ScoreRing` | `Components.kt` | Animated arc + score number |
| `PokeBadge` / `PokeTagPill` | `Components.kt` | Tag chips (9–11 sp, `CircleShape`) |
| `RarityTierCard` | `Components.kt` | Big tier label + score |
| `StatCard` / `IvCard` | `Components.kt` | Key-value stat containers |
| `SectionLabel` | `Components.kt` | Uppercase 10 sp section header |
| `PokeHorizontalDivider` | `Components.kt` | 1 dp divider |
| `PokemonListCard` | `PokemonListCard.kt` | List row (score ring + name + badges + CP) |
| `StitchBottomNavigation` | `StitchNavigation.kt` | Bottom nav bar with centre scan FAB |
| `OverlayTagPill` | `OverlayComponents.kt` | Tag chips for overlay context |
| `OverlayStatCell` | `OverlayComponents.kt` | Stat cell for overlay |
| `OverlayActionButton` | `OverlayComponents.kt` | CTA button (gradient or ghost) |
| `DecisionSupportSection` | `DecisionSupportComponents.kt` | Event/mismatch/recognition notes |
| `FeedbackSection` | `DecisionSupportComponents.kt` | Collapsible error report buttons |
| `ResultShareRenderer` | `ResultShareRenderer.kt` | Canvas-drawn share card (1200×1600) |

### 1.3 State Coverage

| State | Current implementation | Quality |
|---|---|---|
| **Empty** | `StitchEmptyState` in `CollectionScreen` | ✅ Exists, contextual copy |
| **Loading** | Splash progress bar only | ⚠️ No skeleton / shimmer in collection |
| **Error** | Toast-only | ❌ No dedicated error screen or inline error |
| **Scanning active** | FAB text toggles "SCAN NOW / STOP" | ✅ Works but single-state |

---

## 2 · Design Audit

### 2.1 Typography

**Font:** Outfit (Regular → Black) — excellent choice. Modern, geometric, weights render cleanly on OLED.

| Finding | Severity | Detail |
|---|---|---|
| **Line-height < font-size** on `displayLarge` | 🔴 Critical | `46 sp` size, `44 sp` lineHeight → clipping on descenders/multi-line |
| **Body text exclusively Bold/Black** | 🟡 Medium | Nearly every body text is `FontWeight.Bold` or heavier. Reduces visual hierarchy — nothing recedes. |
| **Excessive letter-spacing variance** | 🟡 Medium | `SectionLabel` has `3 sp`, `labelSmall` has `4 sp`, `PokeBadge` uses `1 sp` — three different tracking scales for similarly sized text. |
| **`displayMedium` at 90 sp** unused | 🟢 Low | Defined in `Typography.kt` but never referenced. Dead token. |
| **9 sp text used extensively** | 🟡 Medium | `PokeBadge` (9 sp), filter label (9 sp), stat label (9 sp) — risks WCAG readability on small phones. Minimum should be 10 sp. |
| **Hardcoded Turkish "Geri"** | 🟡 Medium | `OverlayBackButton` has `"Geri"` (Turkish for "Back") instead of a string resource. Breaks i18n and doesn't match English-first UX copy. |

**Recommendation:** Establish a 5-rung type scale (Display / Title / Body / Label / Micro) with consistent weights. Reserve Black for hero numbers only. Promote label minimum to 10 sp.

---

### 2.2 Spacing & Layout

| Finding | Severity | Detail |
|---|---|---|
| **No 4/8-point grid** | 🟡 Medium | Spacings vary: 3, 4, 5, 7, 8, 10, 12, 14, 16, 18, 20, 22, 32 dp. Inconsistent rhythm. |
| **Bento grid fixed 160 dp** | 🟡 Medium | `Row` in `CollectionScreen` is `height(160.dp)` — will clip on larger font/a11y settings. |
| **Bottom nav occludes content** | 🟡 Medium | Content padding `bottom = 148 dp` is a magic number; should derive from nav height + system bars. |
| **Overlay card max height = 76% screen** | 🟢 Low | Reasonable, but no graceful degradation on landscape or foldables. |
| **`ScanResultScreen` type orbs decorative** | 🟢 Low | 220 dp / 280 dp circle decorations are hardcoded absolute positions — may overlap content on short displays. |

**Recommendation:** Adopt 4-point base grid (`4, 8, 12, 16, 24, 32, 48`). Remove fixed heights where content can grow. Use `WindowInsets` for safe-area padding instead of magic numbers.

---

### 2.3 Color & Contrast

**Palette DNA:** Near-black backgrounds (#000 → #131313 → #1A1A1A) + pokéball red accent + rarity tier colours.

| Finding | Severity | Detail |
|---|---|---|
| **Dual colour systems (XML vs Compose)** | 🔴 Critical | `colors.xml` defines `#1A1A2E` (dark navy), Compose `Theme.kt` defines `#000000` (true black). `CollectionScreen` defines its own `#131313`. Three different "dark background" values competing. |
| **Text contrast on dark cards** | 🟡 Medium | `TextHint = #66FFFFFF` (40% white) and `TextMuted = #CCFFFFFF` (80% white) — the hint level fails WCAG AA (3:1) on `Surface1 (#0D0D0D)`. |
| **Rarity color inconsistency** | 🟡 Medium | `RarityColor.Legendary = #FFD700` (Compose) vs `tier_legendary = #FF9800` (XML) vs `tierColor("LEGENDARY") = #F59E0B` (ShareRenderer). Three different "legendary gold" values. |
| **OverlayComponents re-declares colours** | 🟡 Medium | `OverlayTagPill` defines `#FFD700`, `#00FF8C`, `#FFAA00` inline — same semantic values as theme tokens but hardcoded separately. |
| **Light theme is placeholder** | 🟢 Low | `LightColorScheme` exists but the app forces dark. History XML uses `background_dark` unconditionally. |
| **CollectionScreen local shadows XML colours** | 🟡 Medium | `TextMuted = #AC8880` locally, while theme `TextMuted = #CCFFFFFF`. Confusing dual naming. |

**Recommendation:**
1. Single source-of-truth colour file — remove XML colour duplication, reference Compose tokens only.
2. Create a `RarityPalette` object mapping each tier to exactly one colour (primary, dimmed variant, text variant).
3. Raise hint text to ≥ 45% alpha (#73FFFFFF) for WCAG AA on true-black.

---

### 2.4 Visual Hierarchy

| Finding | Severity | Detail |
|---|---|---|
| **Everything bold → nothing stands out** | 🟡 Medium | List card: name is Bold, date is Regular, CP label is SemiBold, CP value is Bold, type badge is Bold. The "important" items don't pop because the "unimportant" items are also bold. |
| **Scan result screen: two competing heroes** | 🟡 Medium | Pokémon name (34 sp Black) and `RarityTierCard` label (34 sp Black) sit side by side at equal visual weight. Reader's eye doesn't know where to look first. |
| **Section labels too quiet** | 🟢 Low | `SectionLabel` at 10 sp / 30% alpha is nearly invisible. A section divider label should be readable. |
| **Bento "Today's Finds" card lacks data viz** | 🟢 Low | Just numbers. A mini bar chart or sparkline would add both visual interest and information density. |

**Recommendation:** Introduce a clear Z-pattern: hero number (highest weight/size) → primary label → secondary metadata → tertiary hint. Reduce body/label weights to Regular/Medium, keep Bold/Black for <2 elements per card.

---

### 2.5 Motion & Animation

| Finding | Severity | Detail |
|---|---|---|
| **ScoreRing animation: solid** | ✅ Good | 900 ms FastOutSlowIn arc sweep — premium feel, appropriate delay cascade. |
| **List card fade+slide: solid** | ✅ Good | 350 ms stagger with per-item delay — subtle, not distracting. |
| **Scan FAB infinite pulse** | 🟡 Medium | `animateFloat 0.85 → 1.0` RepeatMode.Reverse is fine but the glow ring behind it (`alpha = 0.3`) is too faint to notice. Either make the pulse visible or remove it. |
| **Overlay card slide-up** | ✅ Good | 450 ms slide + 400 ms fade — feels smooth. |
| **Score count-up** | ✅ Good | 900 ms score animation with 500 ms delay for stagger — nice reveal. |
| **Missing: exit/dismiss animations** | 🟡 Medium | `onDismiss` in overlay card is instant. No slide-down or fade-out. |
| **Missing: shared-element transition** | 🟢 Low | Navigating from list card to detail has no visual continuity. Score ring could morph. |
| **Splash animation is View-based** | 🟢 Low | Uses `AnimationUtils.loadAnimation(R.anim.scale_up)` — works but could be more fluid with Compose animation. |
| **No haptic integration with animation** | 🟢 Low | `hapticsEnabled` pref exists but no actual haptic calls at animation completion. |

**Recommendation:** Add matching exit animations (reverse of entrance). Consider shared-element transition for list→detail (score ring + name). Wire haptics to score reveal completion.

---

### 2.6 Responsive & Adaptivity

| Finding | Severity | Detail |
|---|---|---|
| **No landscape handling** | 🟡 Medium | All screens are portrait-assumed. Overlay `maxCardHeight = 76%` would be too short landscape. |
| **No tablet/foldable breakpoints** | 🟡 Medium | Collection grid is `LazyColumn` single-column only. On 8" tablet, cards stretch edge-to-edge looking sparse. |
| **History uses View/XML, rest is Compose** | 🟡 Medium | Two UI toolkits = two styling systems. Can't share design tokens cleanly. |
| **Font doesn't scale with display density** | 🟢 Low | All sizes in `sp` (correct), but fixed container heights (`160.dp`) can clip with large font settings. |

**Recommendation:** For v1: lock to portrait. For v2: add `maxWidth` constraint on cards (e.g., 480 dp) for tablets. Migrate `HistoryActivity` to Compose to unify the design system.

---

### 2.7 UX Copy & Microcopy

| Finding | Severity | Detail |
|---|---|---|
| **"Advanced Authentication Engine"** | 🔴 Critical | Splash subtext reads like a fintech API product, not a Pokémon scanner. Misleading. |
| **"SECURED / ENGINE READY / Verifying Cores"** | 🟡 Medium | Cool hacker-terminal aesthetic, but actually confusing — users don't know what "cores" means. |
| **"Active scanning enabled. Sensors optimized for rare signatures."** | 🟡 Medium | Sounds like radar firmware. Replace with something like "Ready to scan. Open Pokémon GO and view any Pokémon." |
| **"LIVE FREQUENCY" / "LIVE STREAM"** | 🟡 Medium | These labels suggest real-time network data, but the collection is just a local list. |
| **Filter chip labels** | ✅ Good | "All / Legendary / Rare / Shiny / Lucky" — clear and useful. |
| **Feedback button labels** | ✅ Good | "Wrong species / Wrong event / Wrong costume / Wrong shiny" — specific and actionable. |
| **"Geri" hardcoded in OverlayBackButton** | 🟡 Medium | Should be `stringResource(R.string.back)` for i18n. |
| **Dialog copy** | ✅ Good | Consent dialog is transparent and well-structured. |

**Recommendation:** Replace military/hacker microcopy with confident-but-approachable language. The scanner is a *companion*, not a surveillance system. Keep the premium tone but make it feel like a Pokédex upgrade.

---

## 3 · Design Language Assessment

### What's Working Well ✅

1. **Outfit typeface** — modern, clean, excellent weight range.
2. **True-dark OLED base** — saves battery, feels premium.
3. **Pokéball red accent** (#E3350D) — immediately says "Pokémon" without being childish.
4. **Type-coloured gradient backgrounds** on detail screen — contextual and beautiful.
5. **ScoreRing component** — the animated arc is the app's signature visual. Keep it.
6. **Rarity tier cards with colour-coded borders** — clear information hierarchy.
7. **Tag pills** — compact, scannable, good use of type colour.
8. **Bottom nav centre FAB** — familiar mobile pattern with strong brand gradient.
9. **Share card renderer** — Canvas-drawn cards are a premium touch.

### What Needs Work ⚠️

1. **Dual UI toolkit** — View/XML (History, Splash) and Compose exist in parallel with separate design tokens.
2. **Colour fragmentation** — same semantic colour exists in 3+ places with different hex values.
3. **Typography weight abuse** — Bold/Black used everywhere dilutes hierarchy.
4. **Missing states** — no loading skeleton, no error state, no offline indicator.
5. **UX copy dissonance** — military/hacker language vs Pokémon fan tool identity.
6. **No iconography system** — uses Material Rounded icons (fine) but no Pokémon-flavoured custom icons.
7. **No empty-state illustration** — just text. A sleeping Snorlax or empty Pokéball would add personality.
8. **Overlay exit animation missing** — enters beautifully, vanishes instantly.
9. **Inconsistent corner radii** — 6, 10, 14, 16, 18, 20, 22, 26 dp all appear. Should be 3 tiers max.

### Anti-Patterns to Avoid 🚫

- ❌ Generic purple gradient (no purple gradients exist — good)
- ❌ AI SaaS nested card spam (the overlay card has one level of nesting — acceptable)
- ❌ Weak grey text (some hint text is too faint — fixable)
- ❌ Excessive bounce animation (no bounce anywhere — good)
- ❌ Web landing page hero copied to mobile (no parallax/scrolljacking — good)

---

## 4 · Design Token Summary

### Current Token Reality

```
Backgrounds:  #000000, #0D0D0D, #111111, #131313, #161616, #1A1A1A, #1A1A2E, #1C1B1B, #2A2A2A
Borders:      #161616, #2A2A2A, #0F0F0F
Text:         #FFFFFF, #CCFFFFFF, #66FFFFFF, #E5E2E1, #AC8880, #B0BEC5, #78909C
Accent:       #E3350D (pokéball red), #00FF8C (green), #FFD700 (gold)
Stripe:       #FF5500 → #E0003C → #9900CC
Corner radii: 6, 10, 14, 16, 18, 20, 22, 26 dp
Spacings:     3, 4, 5, 7, 8, 10, 12, 14, 16, 18, 20, 22, 32 dp
```

### Recommended Consolidated Tokens

```
Backgrounds:  bg-base (#000000), bg-card (#0F0F0F), bg-elevated (#181818), bg-overlay (#1E1E1E)
Borders:      border-subtle (#1A1A1A), border-default (#2A2A2A), border-emphasis (#3A3A3A)
Text:         text-primary (#FFFFFF), text-secondary (#B0B0B0), text-muted (#737373)
Accent:       pokéball-red (#E3350D), accent-green (#00FF8C), accent-gold (#FFD700)
Corner radii: sm (8dp), md (16dp), lg (24dp), pill (CircleShape)
Spacings:     4, 8, 12, 16, 24, 32, 48 dp
```
