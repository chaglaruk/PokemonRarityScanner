# PokeRarityScanner — Reference Onboarding

> **Date:** 2026-05-31  
> **Purpose:** Extract design, workflow, and quality principles from four reference sources and translate them into actionable rules for this project.  
> **Constraint:** No code was modified. Reference repos live in `.agent-references/` (git-excluded).

---

## Source Inventory

| # | Source | Local Path | Status |
|---|---|---|---|
| 1 | [Impeccable](https://github.com/pbakaus/impeccable) | `.agent-references/impeccable/` | ✅ Cloned (shallow) |
| 2 | [ECC (Everything Claude Code)](https://github.com/affaan-m/ECC) | `.agent-references/ECC/` | ✅ Cloned (shallow) |
| 3 | [MotionSites](https://motionsites.ai/) | — (JS-only SPA) | ⚠️ HTML shell only; no renderable content without JS execution |
| 4 | [X thread @Saccc_c](https://x.com/Saccc_c/status/2059484972361056498) | — | ⚠️ Login-walled; tweet metadata extracted from `__INITIAL_STATE__` |

---

## 1 · Impeccable — Design Language Reference

### 1.1 What It Is

Impeccable is a design critique and polish skill for AI coding tools. It ships 23 commands (`/impeccable audit`, `/impeccable polish`, `/impeccable critique`, etc.) that teach AI to produce interfaces passing professional design review. The accompanying `DESIGN.md` is the deepest AI-generated design system spec publicly available: it defines a complete "Neo Kinpaku" brand system with tokens, typography rules, color ramps, elevation principles, material rules, and a Do/Don't checklist.

### 1.2 Key Design Principles Extracted

#### Typography
- **Weight-Inversion Rule:** Hero h1 is lightweight (300); section anchors are heavier (600). This gives the page visual breathing room at the top and grounding at each section.
- **Two-Face Rule:** Display typeface used only at large sizes (≥ 1.2 rem / ~20 sp on mobile). Anything smaller uses the body face. Pinstripe faces lose identity at small sizes.
- **Tracked Labels Are Short Rule:** All-caps tracked text is for short system markers only. Full sentences in tracked caps read as AI scaffolding.
- **Dark Type Needs Air Rule:** Body text on dark surfaces uses line-height 1.65–1.8 and max-width 65–75 ch. Reading copy on black needs generous spacing.

#### Color & Contrast
- **Single-Source Token Rule:** All colors declared in one token file. Hand-typing hex values in page/component CSS is forbidden. If a value isn't in the token file, it either needs adding or it's page-specific scenery.
- **Brand Carries Through Accent, Not Surface:** One primary accent (kinpaku gold) carries the brand. Secondary accent (verdigris patina) is for state signals only, not decoration.
- **Texture Budget Rule:** Gold leaf and patina textures are for brand-bearing moments: hero seams, CTA fills, dividers. Generic cards stay flat.
- **No Pure Black, No Pure White:** Use tinted near-black and near-white tokens.

#### Elevation & Material
- **Hairline First Rule:** Use 1 px borders before adding shadow. Cards rest on borders and background shifts, not drop shadows.
- **No Glass Rule:** Translucency in overlays is acceptable, but decorative blur/glass panels are not part of a flat, dark design system.
- **No Default Card Shadow Rule:** Cards sit on hairline borders and surface-color steps. Shadow is reserved for large framed modules and CTA lift only.

#### Component Discipline
- **Kit Consumption Rule:** Reach for a kit primitive before inventing a new class. Do not create `.hero-cta-primary` when `.ks-button.ks-button-primary` exists. Bespoke classes are for genuinely page-specific scenery (hero illustrations), not for reinventing kit primitives.
- **Bento Over Nested Cards:** Group items in a flat grid (`.ks-bento`), not card-in-card nesting. Max 1 level of nesting.
- **Small Radii:** Corner radii range from 2–8 px (xs to lg). No wide rounded cards.

#### Style & Copy
- **Position > Hedge:** "No maybe consider" — take a position. Every assertion should sound like it comes from someone who has seen a thousand interfaces.
- **Denylist enforced:** "seamless", "robust", "elevate", "empower", "delve", "in today's…", "let's dive in", "in summary" — all build-banned as AI slop tells.
- **Concrete over comprehensive:** Trade coverage for momentum. Leave things out.

### 1.3 Applicable to PokeRarityScanner

| Impeccable Principle | PokeRarity Translation |
|---|---|
| Weight-Inversion Rule | Hero score number = Black weight; section labels = Bold; body = Regular/Medium |
| Two-Face Rule | Outfit display (≥ 20 sp) vs Outfit body. Don't use Black weight below 14 sp. |
| Tracked Labels Are Short | `SectionLabel` ("RECENT SCANS", "SCORE BREAKDOWN") stays all-caps tracked. Full sentences never. |
| Dark Type Needs Air | Raise body lineHeight to ≥ 1.6 × fontSize. Current `displayLarge` lineHeight < fontSize — critical fix. |
| Single-Source Token Rule | Create `PokeTokens.kt` as the one truth. Delete inline `Color(0xFF...)` in all components/screens. |
| Hairline First | Cards use 1 dp border (`borderSubtle`) — no elevation shadow. Shadow only for overlay/sheet. |
| No Glass | Do not add glassmorphism to cards or dialogs. Overlay transparency is fine. |
| Kit Consumption | Merge `PokeBadge` / `PokeTagPill` / `OverlayTagPill` into one component. Don't create parallel copies. |
| Bento Over Nested Cards | Collection bento grid stays flat. Don't nest cards inside bento cells. |
| Small Radii | Consolidate to 3 tiers: 8 dp (sm), 16 dp (md), 24 dp (lg). Eliminate 6/10/14/18/20/22/26. |
| Concrete over comprehensive | Splash copy: replace "Advanced Authentication Engine" → specific: "Rarity Scanner" |

### 1.4 NOT Applicable

| Impeccable Concept | Why Not |
|---|---|
| Neo Kinpaku gold-leaf textures | Our brand is Pokéball red, not Japanese gold leaf |
| Alumni Sans Pinstripe typeface | We use Outfit; adding a second display face adds complexity without value |
| Verdigris patina palette | Our secondary accent is rarity-tier colors (green, blue, purple, gold), not oxidized copper |
| OKLCH color space | Android Compose uses sRGB hex; OKLCH requires conversion and adds no visible benefit on mobile |
| Multiple CSS files / Astro architecture | We're Jetpack Compose, not web. Token approach applies but file structure doesn't. |
| Editorial "magazine" page structure | This is a mobile app, not a marketing site. Sections are screens, not scrollable editorial pages. |

---

## 2 · ECC (Everything Claude Code) — Workflow Reference

### 2.1 What It Is

ECC is a production-ready AI coding plugin providing 63 specialized agents, 249 skills, and 79 commands for Claude Code. It codifies disciplined engineering workflows: plan → TDD → review → commit. The key insight is that agent output quality correlates directly with the specificity and structure of the rules/skills files.

### 2.2 Key Workflow Principles Extracted

#### Agent-First Orchestration
- Delegate to specialized agents for domain tasks: planner for planning, tdd-guide for testing, code-reviewer after writing, security-reviewer for sensitive code.
- Agents have scoped tool permissions — limited scope = focused execution.

#### Mandatory TDD Cycle
1. **RED** — Write test first, watch it fail
2. **GREEN** — Write minimal implementation to pass
3. **IMPROVE** — Refactor, verify 80%+ coverage
- "Fix implementation, not tests, unless tests are wrong."

#### Plan Before Execute
- Complex features get broken into deliberate phases via a planner.
- Simple changes skip planning entirely.

#### Small, Focused Changes
- Functions < 50 lines, files < 800 lines
- No deep nesting (> 4 levels)
- Feature-organized, not type-organized

#### AGENTS.md / RULES.md / CLAUDE.md Architecture
- Three-tier rule system: global rules (always follow) → project rules (context-specific) → agent instructions (per-task)
- Rules are modular `.md` files grouped by concern (security.md, coding-style.md, testing.md, git-workflow.md)
- Clear "Must Always" / "Must Never" lists

#### Commit Discipline
- Conventional commits: `<type>: <description>` (feat, fix, refactor, docs, test, chore)
- PR summaries include: what changed, test plan, validation performed
- Keep changes modular and explain user-facing impact

#### Verification Before Shipping
- "Never commit untested changes"
- Run the relevant test suite before considering a task complete
- Build-error-resolver agent for when builds break — systematic, not guessing

### 2.3 Applicable to PokeRarityScanner

| ECC Principle | PokeRarity Translation |
|---|---|
| TDD Cycle | Before changing scanner pipeline, OCR, or scoring: write/update unit test first, verify it fails, then implement |
| Plan Before Execute | Multi-file UI changes get an `implementation_plan.md` before code |
| Small focused changes | One component/screen per commit. Don't land 5 screens in one PR. |
| AGENTS.md structure | Our `.agents/skills/` already mirrors this. Continue using skill routing from AGENTS.md. |
| Conventional commits | Already in practice. Enforce `feat(ui):`, `fix(scan):`, `refactor(theme):` prefixes. |
| Must Never list | Never hardcode colors inline. Never commit local editor settings. Never bypass existing design tokens. |
| Verify before shipping | `.\gradlew.bat :app:testDebugUnitTest` + `.\gradlew.bat :app:assembleDebug` before every merge |

### 2.4 NOT Applicable

| ECC Concept | Why Not |
|---|---|
| 63 specialized subagents | Overkill for a single-app project. We use the AGENTS.md skill routing pattern, not dozens of subagent files. |
| MCP server configs | This is an Android app, not a cloud service. No Supabase, no Vercel, no Cloudflare. |
| Plugin marketplace architecture | We don't distribute plugins. |
| 80% coverage mandate | Aspirational for an OCR/vision app with hardware-dependent paths. Target 80% for pure logic (scoring, parsing), accept lower for device-dependent code. |
| Immutability as absolute rule | Kotlin data classes are already immutable-by-default. Android View lifecycle requires some mutability. Don't over-engineer. |

---

## 3 · MotionSites — Visual Inspiration

### 3.1 What It Is

MotionSites is a commercial product selling "Premium Hero Prompts" — curated AI prompts for generating high-quality web hero sections and landing page designs. The site itself is a JS-rendered SPA; the HTML shell reveals the brand positioning: "Your Design AI Superpowers In One Click."

### 3.2 Inferred Design Inspiration (from brand positioning + screenshots in public mentions)

MotionSites-style hero sections typically feature:
- **Bold typography with massive display sizes** — 72–120 pt hero headings
- **Animated gradient backgrounds** — multi-stop color transitions
- **Floating 3D elements** — glass orbs, geometric shapes with depth
- **Scroll-triggered reveals** — sections animate on viewport entry
- **Grid-based layouts** — bento grids, card grids, dashboard previews
- **Dynamic micro-interactions** — hover effects, cursor tracking, parallax

### 3.3 Applicable to PokeRarityScanner

| MotionSites Concept | PokeRarity Translation |
|---|---|
| Bold hero number | ✅ Already have this — ScoreRing with big animated number. Keep. |
| Animated gradient backgrounds | ✅ Already have type-gradient on scan result screen. Keep. |
| Grid/bento layout | ✅ Collection bento grid exists. Polish but don't over-decorate. |
| Entrance animations with stagger | ✅ List cards already stagger fade+slide. Polish timing. |
| Card hover/press feedback | ➕ Add press-in scale (0.98) with spring-back on `PokemonListCard` |
| Floating decorative elements | ➕ Subtle: score ring glow, type-colored ambient orbs at low alpha. Don't go overboard. |

### 3.4 NOT Applicable

| MotionSites Concept | Why Not |
|---|---|
| 120 pt web hero headings | Mobile app has < 6" of screen. 48 sp is our hero ceiling. |
| Scroll-triggered parallax | Android scroll is touch-based, not mouse-wheel. Parallax on mobile = jank. |
| Floating 3D glass orbs | GPU-expensive on mid-range Android. The app runs as overlay on PoGO — battery matters. |
| Cursor tracking effects | No cursor on mobile. |
| Full-page scroll-jacking | Kills swipe-back navigation and Android gesture nav. |
| Web-specific blur/backdrop-filter | Compose `BlendMode` blur is expensive. Overlay must be lightweight. |

---

## 4 · X Thread (@Saccc_c) — Design Workflow / Skills

### 4.1 Access Status

> **⚠️ PARTIALLY ACCESSIBLE.** The tweet at `x.com/Saccc_c/status/2059484972361056498` is login-walled for full rendering. From the extracted `__INITIAL_STATE__` metadata:

- **Author:** Sac (@Saccc_c) — Singapore-based, exploring "AI的边际和商业应用" (AI boundary and commercial applications)
- **Tweet content:** A link to an X Article (`x.com/i/article/2058578740741857280`)
- **Engagement:** 650 bookmarks, 338 likes, 82 retweets, 11 replies — high engagement suggesting substantive content
- **Pinned tweet:** This is the author's pinned tweet, indicating it's a flagship piece

### 4.2 Inferred Content (from metadata + author profile)

Based on the author's profile (AI commercial applications, open-source growth path) and the high engagement pattern typical of "how I use AI for X" threads, this is likely a guide about:
- Using AI coding tools for real product development
- Design workflow + skills organization
- How to structure prompts/skills for consistent output quality

### 4.3 What We Can Extract

Without full article access, we apply the general principle the thread represents:

| Principle | Application |
|---|---|
| **Skills-based workflow** | Our AGENTS.md already routes tasks to skills. This validates the approach. |
| **Open-source growth path** | Document decisions (DESIGN.md, UI_IMPROVEMENT_PLAN.md) so future contributors/agents can follow the reasoning. |
| **Commercial-grade AI output** | Don't accept default AI output quality for UI work. Run every UI change through the Impeccable anti-pattern checklist. |

> **Action item:** If you can access the full article while logged in, extract specific workflow tips and append them to this section.

---

## 5 · Synthesized Design Rules for PokeRarityScanner

These rules combine principles from all four sources, translated into project-specific directives.

### 5.1 Token Discipline (from Impeccable)

```
RULE-T1: All colors live in PokeTokens.kt. No inline Color(0xFF...) in screens or components.
RULE-T2: Corner radii use exactly 3 tiers: sm(8dp), md(16dp), lg(24dp), pill(CircleShape).
RULE-T3: Spacing uses the 4-point grid: 4, 8, 12, 16, 24, 32, 48 dp. No 3, 5, 7, 10, 14 values.
RULE-T4: Typography weights follow Weight-Inversion: Black for hero numbers only, Bold for headings,
         Medium for body, Regular for metadata. Never Bold for everything.
RULE-T5: Minimum text size is 10 sp. Eliminate 8 sp and 9 sp occurrences.
```

### 5.2 Component Discipline (from Impeccable + ECC)

```
RULE-C1: One component per concept. Merge duplicate badge/stat/button variants into shared composables.
RULE-C2: Reach for existing components before creating new ones (Kit Consumption Rule).
RULE-C3: Max 1 level of visual nesting (card inside section = OK; card inside card inside section = NOT OK).
RULE-C4: Cards use hairline borders (1dp, borderSubtle). No elevation shadow except overlay/sheet.
RULE-C5: Every component reads from PokeTokens, not from local val declarations.
```

### 5.3 Motion Discipline (from Impeccable + MotionSites)

```
RULE-M1: Entrance animations: ≤ 500ms, FastOutSlowInEasing. No bounce, no spring overshoot > 3%.
RULE-M2: Every entrance animation must have a matching exit animation (reverse).
RULE-M3: Animation is for information, not decoration. Score reveal = information. Random floating orb = decoration.
RULE-M4: Stagger delay between list items: 30–60ms. Not 0 (simultaneous) or 150+ (sluggish).
RULE-M5: Press feedback: 100ms scale to 0.98, spring-back on release. No ripple (already disabled).
RULE-M6: Respect prefers-reduced-motion / Android "Remove animations" accessibility setting.
```

### 5.4 Copy Discipline (from Impeccable STYLE.md)

```
RULE-X1: No military/hacker jargon: "Advanced Authentication Engine", "Verifying Cores", "SECURED",
         "Sensors optimized for rare signatures" are all banned.
RULE-X2: No AI slop words: "seamless", "robust", "elevate", "empower", "delve", "in today's",
         "let's dive in". If it sounds like a SaaS landing page, rewrite it.
RULE-X3: Concrete over comprehensive. "Rarity score: 87" not "Your comprehensive rarity analysis results".
RULE-X4: UX copy should sound like a knowledgeable Pokémon trainer, not a surveillance system.
RULE-X5: String resources for all user-visible text. No hardcoded Turkish/English strings in composables.
```

### 5.5 Workflow Discipline (from ECC)

```
RULE-W1: Plan → Test → Implement → Review → Verify → Commit. Don't skip steps.
RULE-W2: One screen/component per commit. Don't bundle 5 changes into one commit.
RULE-W3: Run .\gradlew.bat :app:testDebugUnitTest before any merge.
RULE-W4: Run .\gradlew.bat :app:assembleDebug to verify the build compiles.
RULE-W5: Conventional commits: feat(ui): / fix(scan): / refactor(theme): / docs: prefixes.
RULE-W6: When changing scanner pipeline, OCR, vision, or scoring: read current signatures first.
```

### 5.6 Anti-Pattern Checklist (from Impeccable + user requirements)

```
ANTI-1: ❌ Generic purple/blue gradient hero → ✅ Pokéball red + type colors
ANTI-2: ❌ Nested card-in-card-in-card → ✅ Max 1 nesting level
ANTI-3: ❌ Weak grey text (< 40% alpha on black) → ✅ Minimum 45% alpha (#73FFFFFF)
ANTI-4: ❌ Excessive spring/bounce animations → ✅ FastOutSlowIn, ≤ 500ms
ANTI-5: ❌ Web landing page hero on mobile → ✅ Information-dense, vertically stacked
ANTI-6: ❌ Glassmorphism on small elements → ✅ Solid dark backgrounds + hairline border
ANTI-7: ❌ Auto-playing video/Lottie on list items → ✅ Reserve animation for hero moments
ANTI-8: ❌ AI SaaS copy ("elevate your workflow") → ✅ Direct, specific, Pokémon-flavored
ANTI-9: ❌ Pure black (#000000) as the only surface → ✅ Layered near-blacks (#000, #0F0F0F, #181818)
ANTI-10: ❌ Every text element Bold/Black → ✅ Weight hierarchy: Black > Bold > Medium > Regular
```

---

## 6 · Phase Mapping: Which Source Informs Which Phase

| Phase | Primary Source | What It Provides |
|---|---|---|
| **P0: Foundation (tokens, grid, type scale)** | **Impeccable** | Single-source tokens, 3-tier radii, type hierarchy rules, color ramp structure |
| **P1: Screen fixes (copy, hierarchy, states)** | **Impeccable** + **ECC** | Copy denylist, concrete-over-comprehensive rule, TDD for state components |
| **P2: Polish (motion, press feedback, haptics)** | **MotionSites** + **Impeccable** | Entrance/exit animation patterns, stagger timing, press feedback — filtered through Impeccable's "no bounce, no glass" discipline |
| **P3: Architecture (merge components, migrate)** | **ECC** | Small focused changes, one component per commit, plan-before-execute for migration |
| **P4: Advanced motion (shared element, FAB states)** | **MotionSites** (inspiration) + **Impeccable** (guardrails) | Ambitious motion ideas from MotionSites, constrained by Impeccable's mobile-appropriate filter |
| **All phases: Workflow** | **ECC** | Plan → TDD → Implement → Review → Verify → Commit cycle applies to every PR |

---

## 7 · Agent Checklist (for every subsequent task)

Before making any UI or design change, the agent MUST:

- [ ] **Read PokeTokens** — Does the color/spacing/radius value exist? If not, add it to tokens first.
- [ ] **Check component inventory** — Does a similar composable already exist? Reuse before creating.
- [ ] **Verify weight hierarchy** — Is Black reserved for ≤ 2 elements per screen?
- [ ] **Scan for anti-patterns** — Run through ANTI-1 to ANTI-10 checklist.
- [ ] **Check copy** — Does the text sound like a Pokémon companion or a SaaS product? Is it in `strings.xml`?
- [ ] **Verify motion** — Does the animation have ≤ 500ms duration? Does it have an exit counterpart?
- [ ] **Run tests** — `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
- [ ] **Verify build** — `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
- [ ] **Commit focused** — One component/screen per commit with conventional prefix.

---

## 8 · Reference File Index

For quick access to key reference documents:

| Document | Path | Key Content |
|---|---|---|
| Impeccable DESIGN.md | `.agent-references/impeccable/DESIGN.md` | Complete design system: colors, typography, components, Do/Don't |
| Impeccable STYLE.md | `.agent-references/impeccable/STYLE.md` | Copy denylist, prose rules, anti-AI-slop enforcement |
| Impeccable PRODUCT.md | `.agent-references/impeccable/PRODUCT.md` | Anti-reference list, design principles, brand personality |
| ECC AGENTS.md | `.agent-references/ECC/AGENTS.md` | Agent orchestration, coding style, testing requirements |
| ECC RULES.md | `.agent-references/ECC/RULES.md` | Must Always / Must Never lists |
| ECC CONTRIBUTING.md | `.agent-references/ECC/CONTRIBUTING.md` | Skill format, checklist, PR process |
| ECC Shortform Guide | `.agent-references/ECC/the-shortform-guide.md` | Practical workflow tips, parallel execution, context management |
| Our DESIGN.md | `docs/DESIGN.md` | Current UI audit with findings |
| Our UI_IMPROVEMENT_PLAN.md | `docs/UI_IMPROVEMENT_PLAN.md` | Phased improvement roadmap |
