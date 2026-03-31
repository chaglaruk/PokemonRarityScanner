# Website Jeton-Inspired Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the existing `web/` landing page into a tighter, premium, English-first marketing site with a real radar emblem logo, browser-language-aware translations, corrected Pokemon terminology, and a branded hero demo video.

**Architecture:** Keep all work inside the existing `web/` Next.js app. Centralize copy and locale metadata in one content module, add a lightweight client-side language state that detects browser locale on first visit and persists user choice, replace the old mixed-content landing page with a cleaner section flow, and introduce real brand assets for the header logo and hero video.

**Tech Stack:** Next.js App Router, React, TypeScript, Tailwind CSS, Framer Motion, SVG assets, MP4 hero media

---

## File Map

**Keep and modify**

- `web/app/layout.tsx`
  - Update metadata for English-first copy and localized-friendly page shell.
- `web/app/page.tsx`
  - Keep page entrypoint minimal and pass control to the landing page.
- `web/components/landing-page.tsx`
  - Refactor into language-aware section rendering and remove incorrect content.

**Create**

- `web/components/content/site-content.ts`
  - Central source of truth for supported locales, nav labels, hero copy, value section, features, FAQ, CTA text.
- `web/components/logo.tsx`
  - Reusable radar emblem + wordmark renderer for header and future reuse.
- `web/lib/locale.ts`
  - Browser locale matching, fallback logic, localStorage key, Arabic direction helper if needed.
- `web/public/logo-radar-emblem.svg`
  - Final approved emblem asset.
- `web/public/logo-radar-lockup.svg`
  - Optional emblem + wordmark export for reuse outside React.
- `web/public/hero-demo.mp4`
  - Branded hero video asset.
- `web/public/hero-demo-poster.svg`
  - Poster frame matched to the final logo and hero video.
- `scripts/generate_hero_demo.py` or `scripts/generate_hero_demo.js`
  - Deterministic generator for the first-pass branded hero video if direct capture is not used.

**Possible split if `landing-page.tsx` remains too large**

- `web/components/header.tsx`
- `web/components/hero.tsx`
- `web/components/proof-strip.tsx`
- `web/components/value-strip.tsx`
- `web/components/feature-grid.tsx`
- `web/components/language-switcher.tsx`

**Verification targets**

- `web/package.json`
  - Use `npm run build` as required verification.
- Asset checks
  - Verify `logo-radar-emblem.svg`, `hero-demo.mp4`, and poster file are present and referenced correctly.

---

### Task 1: Lock Localization Content And Browser Language Rules

**Files:**
- Create: `web/components/content/site-content.ts`
- Create: `web/lib/locale.ts`
- Test: `web/package.json` via `npm run build`

- [ ] **Step 1: Write the failing content model in `site-content.ts`**

Create a typed locale model like:

```ts
export const supportedLocales = ["en", "tr", "de", "es", "ar"] as const;
export type SupportedLocale = (typeof supportedLocales)[number];

export const defaultLocale: SupportedLocale = "en";

export const localeLabels: Record<SupportedLocale, string> = {
  en: "English",
  tr: "Türkçe",
  de: "Deutsch",
  es: "Español",
  ar: "العربية",
};
```

Add localized content objects for:
- nav items
- hero
- proof strip
- value section
- features
- steps
- use cases
- FAQ
- CTA/footer

- [ ] **Step 2: Write the failing locale resolution helpers**

Create `web/lib/locale.ts` with exact helpers:

```ts
export const LOCALE_STORAGE_KEY = "prs-locale";
export function matchSupportedLocale(input?: string | null): SupportedLocale
export function detectBrowserLocale(): SupportedLocale
export function isRtlLocale(locale: SupportedLocale): boolean
```

Rules:
- `en-*` => `en`
- `tr-*` => `tr`
- `de-*` => `de`
- `es-*` => `es`
- `ar-*` => `ar`
- unsupported => `en`

- [ ] **Step 3: Run build to verify the new modules compile before wiring**

Run: `npm run build`
Workdir: `web`
Expected: PASS with unused modules not yet imported.

- [ ] **Step 4: Commit**

```bash
git add web/components/content/site-content.ts web/lib/locale.ts
git commit -m "feat: add website locale content model"
```

### Task 2: Create The Final Radar Logo Assets

**Files:**
- Create: `web/public/logo-radar-emblem.svg`
- Create: `web/public/logo-radar-lockup.svg`
- Create: `web/components/logo.tsx`

- [ ] **Step 1: Create `logo-radar-emblem.svg`**

Build the final emblem from approved direction `2`:
- circular dark base
- radar rings
- warm accent scan arm
- compact enough for favicon/header use

- [ ] **Step 2: Create `logo-radar-lockup.svg`**

Create a horizontal lockup:
- emblem on the left
- `POKERARITYSCANNER` wordmark on the right
- export friendly for docs and reuse

- [ ] **Step 3: Create reusable React logo component**

Add:

```tsx
type LogoProps = {
  compact?: boolean;
  showWordmark?: boolean;
  locale: SupportedLocale;
};
```

Use the emblem inline or via `/logo-radar-emblem.svg`, and keep label text consistent across locales.

- [ ] **Step 4: Run build**

Run: `npm run build`
Workdir: `web`
Expected: PASS with asset paths valid.

- [ ] **Step 5: Commit**

```bash
git add web/public/logo-radar-emblem.svg web/public/logo-radar-lockup.svg web/components/logo.tsx
git commit -m "feat: add radar logo assets"
```

### Task 3: Generate The Hero Demo Video Asset

**Files:**
- Create: `scripts/generate_hero_demo.py` or `scripts/generate_hero_demo.js`
- Create: `web/public/hero-demo.mp4`
- Create: `web/public/hero-demo-poster.svg`

- [ ] **Step 1: Choose the simplest generator path available locally**

Inspect whether Python with Pillow/OpenCV or Node with canvas/ffmpeg tooling is already available. Prefer the smallest dependency path already present in the repo or system.

- [ ] **Step 2: Write the generator script**

The video should show:
- branded opening frame with radar emblem
- scanning-style motion overlays
- Pokemon-focused UI wording
- rarity score reveal moment

Keep it short:
- target length: 5-8 seconds
- silent loop-friendly composition

- [ ] **Step 3: Generate `hero-demo.mp4` and poster**

Output files:
- `web/public/hero-demo.mp4`
- `web/public/hero-demo-poster.svg`

- [ ] **Step 4: Verify files exist**

Run:

```bash
dir web\public\hero-demo.mp4
dir web\public\hero-demo-poster.svg
```

Expected: both files present with non-zero size.

- [ ] **Step 5: Commit**

```bash
git add scripts/generate_hero_demo.py web/public/hero-demo.mp4 web/public/hero-demo-poster.svg
git commit -m "feat: add branded hero demo media"
```

### Task 4: Rewrite Header For Brand And Language Selection

**Files:**
- Modify: `web/components/landing-page.tsx`
- Modify: `web/app/layout.tsx`
- Use: `web/components/logo.tsx`
- Use: `web/components/content/site-content.ts`
- Use: `web/lib/locale.ts`

- [ ] **Step 1: Add client-side locale state to the landing page**

Use:
- default locale `en`
- first-load browser detection
- persisted override from `localStorage`

Recommended state shape:

```tsx
const [locale, setLocale] = useState<SupportedLocale>(defaultLocale);
const [hydrated, setHydrated] = useState(false);
```

- [ ] **Step 2: On mount, resolve locale in this order**

1. stored locale
2. browser locale
3. `en`

Persist user changes after manual selection.

- [ ] **Step 3: Add header language selector**

Render a compact selector with:
- visible current locale
- all five locale options
- clean keyboard-friendly button list or select

Do not let the selector dominate the header.

- [ ] **Step 4: Replace old brand block with the radar logo**

Use `Logo` component in the header and hero if needed.

- [ ] **Step 5: Update `<html>` or content direction handling if Arabic is active**

If full dynamic `<html dir>` update is awkward in this phase, at minimum apply a wrapping `dir="rtl"` on Arabic-rendered sections that need it and verify readability.

- [ ] **Step 6: Run build**

Run: `npm run build`
Workdir: `web`
Expected: PASS with client-side locale logic compiling.

- [ ] **Step 7: Commit**

```bash
git add web/components/landing-page.tsx web/app/layout.tsx
git commit -m "feat: add website language selector and radar branding"
```

### Task 5: Rewrite Hero Copy And Media In English-First Form

**Files:**
- Modify: `web/components/landing-page.tsx`
- Modify: `web/components/content/site-content.ts`
- Use: `web/public/hero-demo.mp4`
- Use: `web/public/hero-demo-poster.svg`

- [ ] **Step 1: Replace all incorrect `card` references in hero copy**

All hero text must refer to:
- Pokemon
- Pokemon GO
- scan
- species
- rarity

Never:
- card
- cards
- TCG-like framing

- [ ] **Step 2: Rewrite English hero content first**

Use concise product-first wording with:
- product explanation
- primary CTA `Download App`
- secondary CTA `See How It Works`

- [ ] **Step 3: Add parallel translations for all supported locales**

Translations must remain equivalent in meaning and CTA structure.

- [ ] **Step 4: Wire the real hero video**

Ensure the hero `<video>` uses:
- `poster="/hero-demo-poster.svg"`
- `source src="/hero-demo.mp4"`

Keep controls optional, but autoplay muted loop should be supported.

- [ ] **Step 5: Run build**

Run: `npm run build`
Workdir: `web`
Expected: PASS and no broken asset references.

- [ ] **Step 6: Commit**

```bash
git add web/components/landing-page.tsx web/components/content/site-content.ts web/public/hero-demo.mp4 web/public/hero-demo-poster.svg
git commit -m "feat: rewrite website hero in English-first form"
```

### Task 6: Replace Problem / Solution With A Value-Forward Section

**Files:**
- Modify: `web/components/landing-page.tsx`
- Modify: `web/components/content/site-content.ts`

- [ ] **Step 1: Remove the old `Problem / Solution` block entirely**

Delete the old two-card section without preserving the framing.

- [ ] **Step 2: Add a new `Why Use It` or `What You Get` section**

Use three concise value points such as:
- instant rarity signal
- in-game overlay workflow
- collector, trade, and battle decisions

- [ ] **Step 3: Localize the replacement section**

Provide translation entries for all five locales.

- [ ] **Step 4: Run build**

Run: `npm run build`
Workdir: `web`
Expected: PASS with new section rendering.

- [ ] **Step 5: Commit**

```bash
git add web/components/landing-page.tsx web/components/content/site-content.ts
git commit -m "feat: replace problem section with value messaging"
```

### Task 7: Correct All Product Terminology Across The Page

**Files:**
- Modify: `web/components/landing-page.tsx`
- Modify: `web/components/content/site-content.ts`

- [ ] **Step 1: Search for incorrect terms**

Run:

```bash
rg -n "card|cards|TCG" web
```

Expected: identify all user-facing copy using wrong product framing.

- [ ] **Step 2: Replace with correct domain language**

Use:
- `Pokemon`
- `Pokemon GO`
- `species`
- `scan`
- `rarity score`

- [ ] **Step 3: Re-run the search**

Run:

```bash
rg -n "card|cards|TCG" web
```

Expected: no remaining user-facing incorrect copy in the landing page implementation.

- [ ] **Step 4: Run build**

Run: `npm run build`
Workdir: `web`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web/components/landing-page.tsx web/components/content/site-content.ts
git commit -m "fix: correct website pokemon terminology"
```

### Task 8: Final Polish And Verification

**Files:**
- Modify: `web/components/landing-page.tsx`
- Modify: `web/app/layout.tsx`
- Modify: `web/components/content/site-content.ts`
- Verify: `web/public/logo-radar-emblem.svg`, `web/public/hero-demo.mp4`

- [ ] **Step 1: Check language selector behavior manually**

Verify:
- default English without stored choice
- browser detection for supported locale
- persisted override after manual selection

- [ ] **Step 2: Check mobile header and hero**

Verify:
- logo scale
- selector fit
- CTA wrapping
- hero media balance

- [ ] **Step 3: Check Arabic rendering**

Verify:
- selector label displays correctly
- content remains readable
- alignment and direction are acceptable for this phase

- [ ] **Step 4: Run build**

Run: `npm run build`
Workdir: `web`
Expected: PASS.

- [ ] **Step 5: Capture manual preview evidence**

Open local preview and confirm:
- header logo reads clearly
- language switch changes content
- value section has replaced problem framing
- hero video plays

- [ ] **Step 6: Commit**

```bash
git add web/components/landing-page.tsx web/app/layout.tsx web/components/content/site-content.ts web/public/logo-radar-emblem.svg web/public/hero-demo.mp4
git commit -m "feat: polish website branding localization and hero media"
```

## Implementation Notes

- Use English-first content as the canonical source when translating.
- Do not introduce route-based locale handling in this phase.
- Keep locale logic small and deterministic.
- If Arabic RTL treatment becomes invasive, prioritize readable content and scoped direction changes rather than global refactors.
- Hero video generation should prefer deterministic scripted output over ad-hoc manual assets when possible.
- Keep the logo asset reusable outside the landing page component.

## Verification Checklist

- `npm run build` in `web/`
- Manual browser pass for:
  - English default
  - Turkish browser detection
  - manual override persistence
  - radar emblem visibility in header
  - hero video playback
  - no `card/cards/TCG` language in user-facing copy
