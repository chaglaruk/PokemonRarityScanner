# Website Jeton-Inspired Design

**Goal:** Redesign the existing `website/` marketing site so it captures the premium structural feel that impressed the user on Jeton, while remaining clearly branded as PokeRarityScanner and optimized for two outcomes: fast product comprehension and app downloads.

**Context**

The repository already contains a standalone Next.js marketing site in `website/`, but its current design direction is Pokemon-themed, lighter, and more generic than the target. The user explicitly wants the premium layout discipline and visual confidence they liked on Jeton, but not a literal copy. Through design review, the preferred direction was narrowed to:

- a dark, product-first landing page
- warm orange / amber accents
- premium block composition inspired by Jeton's structure and rhythm
- mockup-first visuals now, with easy replacement by real screenshots later
- primary outcomes of "explain quickly" and "drive download"

The user approved a tighter landing page rather than preserving every existing section.

**Design Direction**

The site should feel like a premium product landing page, not a fan-site or playful game page.

- Visual tone: dark graphite backgrounds, restrained glass surfaces, orange/amber highlights
- Layout tone: spacious, editorial, and premium, but more product-explanatory than fashion/editorial
- Product tone: scanner / detection / analysis / rarity scoring
- Motion tone: subtle reveal, glow, and scan-inspired accents; no loud or ornamental animation system

Jeton inspiration is limited to structure and polish:

- large spacing and clear visual hierarchy
- strong hero composition with mockup-led storytelling
- soft-radius premium content blocks
- disciplined CTA placement and section rhythm

The following should not be copied literally:

- Jeton's exact color palette
- Jeton's wording or brand voice
- Jeton's exact iconography or composition details
- any section sequence that does not serve this product

**Primary UX Objectives**

1. A first-time visitor should understand the product in a few seconds.
2. The page should keep app download as the dominant action.
3. Mockup visuals should be easy to swap later without rewriting layout code.

**Page Architecture**

The landing page should be tightened to the following structure:

1. `Header`
   - Sticky, dark/glass navigation bar
   - Left: brand
   - Middle: short navigation
   - Right: primary `Download App` CTA

2. `Hero`
   - Left: headline, short explainer, primary and secondary CTAs
   - Right: product-first mockup scene
   - The hero must immediately communicate scan -> analyze -> score

3. `Proof Strip`
   - Compact row of 3-4 product trust/fact chips
   - Examples: fast scan, instant rarity score, visual detection, saved history

4. `Feature Showcase`
   - Three large premium product blocks
   - Recommended topics: `Scan`, `Analyze`, `Score`
   - Alternating layout with copy on one side and a mockup slot on the other

5. `How It Works`
   - Very short step-based explanation
   - Recommended flow: open app, scan, detect, score, save

6. `Benefits Grid`
   - Smaller supporting cards
   - Examples: overlay, filters, history, quick results, clean UI

7. `Final CTA`
   - Short, assertive download-focused section
   - Should reinforce the main conversion path rather than introduce new complexity

8. `Footer`
   - Minimal and clean
   - No unnecessary link clutter

**Component Strategy**

The implementation should stay inside the existing `website/` Next.js app and shift it toward a reusable design system rather than a one-off page.

- Add theme tokens for color, spacing, radius, shadows, borders, and glow
- Create reusable section shells so visual rhythm stays consistent
- Use reusable proof-chip components for short trust/fact callouts
- Build large feature blocks that support alternating text/media layouts
- Build a reusable final CTA block

**Mockup Slot Strategy**

Mockups should be deliberately swappable.

The first implementation should use stylized placeholder/mock visuals, but those visuals should live behind reusable slot-style components rather than hardcoded per section. The preferred pattern is:

- `HeroMockup`
- `FeatureMockup`
- `StepMockup`

Each of these should accept content or asset inputs later so the user can replace placeholders with real app screenshots or short visuals without reworking page structure.

This means the first release is mockup-first, but not throwaway.

**Visual System**

- Backgrounds: deep graphite / near-black layered surfaces
- Accent range: warm orange to amber
- Surface treatment: restrained glass and soft border glow
- Typography: strong display headings with clean technical body text
- Corners: generous radius to preserve premium softness
- Motion: subtle reveal transitions, light scan-line or signal cues when useful, no aggressive motion-heavy spectacle

**Content Guidance**

Copy should be concise and product-centered.

- Headline copy must explain what the app does, not speak in vague brand slogans
- Secondary copy must reduce uncertainty quickly
- CTA hierarchy should stay stable across the page:
  - primary: `Download App`
  - secondary: `See How It Works`

Testimonials and weak social-proof filler should not be preserved unless the project has credible, concrete material to show. The approved direction is a tighter product landing page rather than a generic SaaS template full of low-trust filler sections.

**Implementation Constraints**

- Preserve the existing `website/` project rather than spinning up a second site
- Prefer modular components over one large monolithic page file
- Preserve easy replacement of mockups and content later
- Keep the page responsive across mobile and desktop
- Favor polish and clarity over adding more sections

**Non-goals**

- No literal Jeton clone
- No Pokemon-themed playful redesign
- No CMS layer unless later requested
- No requirement to keep every current section from the existing marketing page
- No dependency on real screenshots for the first pass

**Open Inputs Already Resolved**

The following decisions were already approved during brainstorming:

- Direction: Jeton-inspired but adapted to PokeRarityScanner
- Tone: dark background with orange highlights
- Goals: explain quickly and drive downloads
- Media strategy: begin with mockups that are easy to replace later
- Overall aesthetic approach: `Product-First Tech`
- Scope approach: tighter landing page instead of preserving every current section

**Verification for Implementation Planning**

A later implementation plan should be able to answer these concretely:

- which current sections/components are removed, merged, or rewritten
- where theme tokens live
- which reusable mockup-slot components are introduced
- how the hero and showcase sections are composed
- how responsiveness is handled across hero, showcase, and CTA sections
- how the current content is rewritten to match the approved direction
