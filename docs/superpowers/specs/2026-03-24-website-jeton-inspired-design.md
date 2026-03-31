# Website Jeton-Inspired Design

**Goal:** Redesign the existing `web/` marketing site so it feels premium, product-led, and conversion-focused for PokeRarityScanner, while improving brand identity, fixing product terminology, and supporting multiple languages with English-first behavior.

**Context**

The repository currently uses the `web/` Next.js app for the public marketing site. The current landing page has three issues that must be corrected as part of the redesign:

- branding is not yet strong enough because the site lacks a proper emblem logo
- parts of the copy use incorrect product language such as "cards" instead of "Pokemon"
- the page is effectively single-language, while the user wants English-first with automatic browser-language matching

The user also wants the hero area to use a real branded demo video rather than a static placeholder-only treatment.

**Approved Direction**

The site should remain a dark, premium, product-first landing page with warm orange / amber accents and disciplined section rhythm. It should not feel playful or fan-site-like. The approved updates on top of the earlier direction are:

- logo direction: emblem logo using the approved `radar scan emblem`
- default content language: English
- available languages in selector:
  - English
  - Türkçe
  - Deutsch
  - Español
  - العربية
- language behavior:
  - default content is English
  - if the browser language matches one of the supported locales, the first visit should show that locale
  - user-selected language should override detection after selection
- terminology correction:
  - never refer to Pokemon GO entities as "cards"
  - use `Pokemon`, `Pokemon GO`, `scan`, `rarity`, `species`, `overlay`, `analysis`
- section change:
  - remove the `Problem / Solution` block entirely
  - replace it with a value-forward section such as `Why Use It` or `What You Get`
- hero media:
  - generate a branded hero demo video asset for the site
  - the video should be easy to replace later, but the first pass should be a real usable asset rather than only a note telling the user to drop one in

**Primary UX Objectives**

1. A first-time visitor should understand the product in a few seconds.
2. The page should keep app download as the dominant action.
3. The site should feel like a real product brand, not just a one-page placeholder.
4. Visitors should see the right language immediately when their browser language is supported.

**Brand Direction**

The brand should feel precise, technical, and premium.

- emblem: radar scan mark
- usage: emblem plus wordmark in the header
- behavior: must work in header, hero, favicon/app-icon follow-up work, and marketing exports
- tone: analytical, confident, clean

The approved radar emblem should communicate scanning and detection at a glance without becoming overly decorative.

**Page Architecture**

The landing page should use this tighter structure:

1. `Header`
   - sticky dark/glass navigation
   - left: radar emblem + brand wordmark
   - right: short nav, language selector, primary download CTA

2. `Hero`
   - left: English-first headline and explainer
   - right: real branded demo video
   - must communicate scan -> analyze -> score on Pokemon, not cards

3. `Proof Strip`
   - compact trust/fact row
   - examples: fast scan, instant rarity score, visual detection, saved history

4. `Value Section`
   - replaces `Problem / Solution`
   - focuses only on what the user gets
   - examples: instant signal, in-game overlay flow, collector/trade/battle decision support

5. `Feature Showcase`
   - three larger premium blocks
   - scan, analyze, score or similarly tight product framing

6. `How It Works`
   - short step-based explanation
   - all terminology must refer to Pokemon in Pokemon GO

7. `Benefits / Use Cases`
   - smaller support cards
   - collector, trade, and battle use cases are acceptable

8. `Final CTA`
   - concise download-focused close

9. `Footer`
   - minimal
   - include language-aware brand/legal copy only if useful

**Internationalization Direction**

The site should use lightweight client-side locale switching for this phase.

- no route-based locale system yet
- content should be centralized in one structured content module
- locale should be determined on first load from the browser when available
- supported language mapping should include common browser tags, for example:
  - `en`, `en-GB`, `en-US` -> English
  - `tr`, `tr-TR` -> Türkçe
  - `de`, `de-DE` -> Deutsch
  - `es`, `es-ES`, `es-MX` -> Español
  - `ar`, `ar-SA`, `ar-AE` -> العربية
- if there is no supported match, fall back to English
- after the visitor chooses a language manually, persist the choice locally and stop auto-switching
- Arabic must display correctly as text content; full RTL polish can be limited to high-value areas in this phase if needed

**Hero Video Direction**

The hero must move from placeholder-only media to a real branded video asset.

- output format should be web-friendly, such as `mp4`
- content should visually support the product story:
  - Pokemon GO scan context
  - overlay / detection feel
  - rarity scoring result moment
- the first pass may be motion-graphics based rather than literal app capture if direct capture is not available
- the component should still support easy replacement by a future real product demo

**Content Guidance**

- all core copy should be written in English first
- translations should be structurally equivalent, not marketing filler
- avoid introducing user-problem framing like "you are struggling with X"
- lead with value and capability, not pain
- CTA hierarchy should remain:
  - primary: `Download App`
  - secondary: `See How It Works`

**Implementation Constraints**

- keep work inside the existing `web/` app
- preserve modularity and reuse
- do not use Pokemon TCG terminology
- do not keep the old `Problem / Solution` section
- add the logo as a real asset, not just a mock
- create a first-pass hero video asset in-repo

**Resolved Inputs**

- emblem style: approved
- chosen logo direction: `radar scan emblem`
- language strategy: client-side selector, English default, browser-language detection on first visit
- supported languages: English, Türkçe, Deutsch, Español, العربية
- section removal: `Problem / Solution` must be removed
- terminology correction: `cards` must be removed from product copy
- hero media: create a real branded hero video

**Verification For Planning**

The implementation plan must answer:

- where the new logo assets live and how they are used in the header
- where localized content and locale detection logic live
- how browser language is resolved and persisted
- how the `Problem / Solution` section is replaced
- where the hero video source asset lives and how it is generated
- which current copy strings must be corrected from `card` terminology to `Pokemon`
- how Arabic is handled in the selector and rendered content
