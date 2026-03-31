# PokeRarityScanner Web Sitesi — Jeton.com Stili Design Brief & Implementation Plan

> **Model:** Jeton.com layout ve UX pattern'ini takip et
> **Platform:** Next.js 14 + Tailwind CSS + Framer Motion
> **Target:** Modern, hızlı, SEO, responsive, mobile-first

---

## 📐 SITE YAPISI (Jeton Pattern'i)

```
/
├── Hero Section
├── Main Value Prop ("Unify your Pokemon Rarity Scoring")
├── Features Grid (Add/Scan/Analyze/Organize)
├── How It Works (5-Step Prozess)
├── Feature Showcase (Rarity Scoring, Visual Detection, Shiny Recognition)
├── Social Proof (User Testimonials)
├── CTA Section ("1M+ Scans Done")
├── Footer (Links, Legal, Social)
```

---

## 🎨 TASARIM KOORDİNATLARI

### Renk Paleti (Jeton'dan ilham, Pokemon teması)
```
Primary: #E3350D (Pokémon Red - Pokéball kırmızısı)
Secondary: #000000 (Pokéball siyah)
Tertiary: #FFDE00 (Pikachu sarısı - accent)
Neutral: #F5F5F5 (Light gray - bg)
Dark: #1A1A1A (Deep black - text)
Success: #4CAF50 (Green - rarity high)
Warning: #FF9800 (Orange - rarity medium)
```

### Typography
```
Headlines: Montserrat Bold / Roboto Bold (weights: 700, 800)
Body: Inter / Roboto Regular (weights: 400, 500)
Mono: JetBrains Mono (for code, stats)
```

### Spacing & Grid
```
Max Width: 1400px
Gutter: 16px (mobile), 32px (desktop)
Padding: 40px (mobile), 80px (desktop)
Gap: 24px (components)
```

---

## 📑 SAYFA BÖLÜMLERI (Section by Section)

### ✅ SECTION 1: Navigation & Header
**Height:** 80px
**Sticky:** Yes
**Content:**
- Logo (PokeRarityScanner + Pokéball)
- Nav Links: Home | Features | How It Works | Download | FAQ | Contact
- Language Toggle: EN | TR
- Download Button (Primary)

```jsx
// Jeton style: Minimalist, clean, white bg, shadow on scroll
header {
  background: white
  box-shadow: 0 0 0 (on scroll: 0 2px 8px rgba(0,0,0,0.1))
  padding: 16px 40px
}
```

---

### ✅ SECTION 2: Hero ("One App for All Pokemon Rarity Needs")
**Height:** 100vh
**Type:** Full-screen hero with background video/gradient

**Text Content:**
```
Headline: "Scan once. Know everything."
Subheading: "The fastest way to discover your rarest Pokemon"
CTA Buttons: 
  - "Download Now" (Primary - Red)
  - "Learn More" (Secondary - Outline)
Social Proof: "Trusted by 150K+ Pokemon trainers"
```

**Visual Elements:**
- Background: Animated gradient + floating Pokéball animations
- Foreground: Device mockup showing the app scanning a Pokemon
- Floating elements: Rarity score badges rotating

```jsx
// Jeton style: Hero with animated gradient, centered content
hero {
  background: linear-gradient(135deg, #E3350D 0%, #FF6B6B 100%)
  height: 100vh
  display: flex
  align-items: center
  justify-content: center
  
  // Floating animations
  @keyframes float {
    0%, 100% { transform: translateY(0px) }
    50% { transform: translateY(20px) }
  }
}
```

---

### ✅ SECTION 3: Main Value Prop ("Unify Your Pokemon Knowledge")
**Height:** 600px
**Layout:** Asymmetric - Image left, text right (or alternating)

**Content:**
```html
<h2>Unify your Pokemon knowledge</h2>

<!-- Tarekesinde şu özellikler gösterilecek -->
- Screenshot app scanning
- OCR text extraction
- Rarity calculation
- Results card display
```

**Structure (2 columns):**
```
Left Column: 
  - Animated mockup of app
  - Scanning sequence shown
  - Result card appearing

Right Column:
  - "All your Pokemon, in one app"
  - "One tap. Get everything you need."
  - Sub-bullets:
    ✓ Instant nadirlik hesabı
    ✓ CV, HP, Level detection
    ✓ Şiny/Shadow/Lucky recognition
    ✓ Tarih ve öğrenme çıkartılması
```

---

### ✅ SECTION 4: Features Grid ("Why PokeRarityScanner?")
**Height:** 700px
**Grid:** 2×2 or 1×4 (responsive)

**4 Main Features (Jeton's "Add/Send/Exchange" style):**

**Feature 1: Scan**
```
Icon: Pokéball icon spinning
Title: "Scan"
Description: "Press the floating Pokéball, point at Pokemon card, done."
Visual: GIF of scanning process
```

**Feature 2: Analyze**
```
Icon: Chart/Graph icon
Title: "Analyze"
Description: "OCR reads CP/HP/Name/Date. AI detects Shiny/Shadow/Lucky."
Visual: Animated flowchart showing data extraction
```

**Feature 3: Score**
```
Icon: Star rating icon
Title: "Score"
Description: "Get 1-100 Rarity Score instantly. Know what you got."
Visual: Rarity score gauge animation (0→92)
```

**Feature 4: Organize**
```
Icon: List/Library icon
Title: "Organize"
Description: "Keep all your scans. Filter by rarity, type, date."
Visual: Screenshot of scan history organized
```

---

### ✅ SECTION 5: How It Works ("5 Simple Steps")
**Height:** 800px
**Type:** Step-by-step visual guide (like Jeton's 5-step process)

**Layout:** Vertical timeline or horizontal carousel (responsive)

```
STEP 01: Start App
  └─ "Launch PokeRarityScanner, see Pokéball overlay"
     [Visual: App icon → Pokéball floating]

STEP 02: Open Pokemon
  └─ "Go to Pokemon GO, find a Pokemon to check"
     [Visual: Pokemon GO screenshot]

STEP 03: Tap Pokéball
  └─ "Tap the floating Pokéball widget"
     [Visual: Finger tapping animation]

STEP 04: Get Results
  └─ "App scans, analyzes, calculates nadirlik"
     [Visual: Scanning progress bar]

STEP 05: See Score
  └─ "View detailed rarity report + recommendations"
     [Visual: Result card with all data]
```

**Design Pattern:**
```css
/* Step circles */
.step-number {
  width: 80px
  height: 80px
  border-radius: 50%
  background: #E3350D
  color: white
  font-size: 24px
  font-weight: bold
  display: flex
  align-items: center
  justify-content: center
}

/* Connecting line between steps */
.step-connector {
  width: 4px
  height: 100px
  background: linear-gradient(180deg, #E3350D, transparent)
}
```

---

### ✅ SECTION 6: Feature Deep-Dive ("Rarity Scoring Engine")
**Height:** 900px
**Type:** Showcase section (2 alternating layouts)

**Subsection A: Real-Time Rarity Analysis**
```
Left: Animated visualization
  - Pokemon card mockup
  - OCR boxes showing detected text
  - Rarity calculation happening in real-time

Right: Text content
  Title: "Real-Time Nadirlik Hesabı"
  Description: "Advanced AI analyzes 15+ factors instantly"
  
  Factors shown:
  • CP Value (Combat Power)
  • HP (Health Points)
  • IV Percentage
  • Catch Date
  • Variant Status (Shiny/Shadow/Lucky)
  • Type Rarity
  • Market Value Score
```

**Subsection B: Smart Visual Detection**
```
Left: Text content
  Title: "Spot Rare Variants Instantly"
  
  Abilities:
  • 🌟 Shiny Detection (Color analysis)
  • 👁️ Shadow Pokemon (Visual markers)
  • ✨ Lucky Pokemon (Glow recognition)
  • 🎭 Regional Variants (Pattern matching)

Right: Visual showcase
  - Gallery of different Pokemon variants
  - Each labeled with detection type
  - Confidence scores shown
```

**Subsection C: Battle-Ready Insights**
```
Right: Text content
  Title: "Know Your Battle Ready Pokemon"
  
  Insights:
  • Best Pokemon for current meta
  • Type advantage recommendations
  • League-specific ratings
  • Training suggestions

Left: Visual showcase
  - League selectors (Great/Ultra/Master)
  - Pokemon recommendations
  - Type effectiveness icons
```

---

### ✅ SECTION 7: Social Proof ("Hear From Our Trainers")
**Height:** 700px
**Type:** Testimonial carousel

**5 Testimonials (Jeton style cards):**

```
Testimonial 1:
  Avatar: Trainer silhouette
  Name: "Ash M."
  Role: "Pokemon Collector"
  Rating: ⭐⭐⭐⭐⭐
  Quote: "Instantly know which Pokemon to keep. Game changer!"
  Tag: "Recommended"

Testimonial 2:
  Name: "Misty K."
  Role: "PvP Battle Master"
  Rating: ⭐⭐⭐⭐⭐
  Quote: "Perfect for team building. Saves hours of analysis."
  Tag: "Game Changer"

Testimonial 3:
  Name: "Brock S."
  Role: "Casual Player"
  Rating: ⭐⭐⭐⭐⭐
  Quote: "So easy to use. Even my kids get it!"
  Tag: "Family Friendly"

Testimonial 4:
  Name: "Cynthia L."
  Role: "Tournament Competitor"
  Rating: ⭐⭐⭐⭐⭐
  Quote: "Essential tool for competitive play. Worth every scan."
  Tag: "Pro Choice"

Testimonial 5:
  Name: "Nurse Joy B."
  Role: "Pokemon Professor"
  Rating: ⭐⭐⭐⭐⭐
  Quote: "Scientific accuracy meets user-friendly design!"
  Tag: "Verified Expert"
```

**Card Design:**
```jsx
.testimonial-card {
  background: white
  border-radius: 16px
  padding: 32px
  box-shadow: 0 4px 12px rgba(0,0,0,0.08)
  border-left: 4px solid #E3350D
  
  .quote {
    font-size: 18px
    line-height: 1.6
    margin: 16px 0
    color: #1A1A1A
  }
  
  .tag {
    display: inline-block
    background: #FFDE00
    color: #000
    padding: 4px 12px
    border-radius: 20px
    font-size: 12px
    font-weight: 600
    margin-top: 16px
  }
}
```

---

### ✅ SECTION 8: Statistics & Social Proof ("By The Numbers")
**Height:** 500px
**Type:** Metric showcase

```
+150K
Active Users

+2.5M
Pokemon Scanned

99%
Accuracy Rate

<2s
Scan Speed

Stats Design:
  - Large numbers (72px bold)
  - Smaller label below
  - Animated counter (0 → final number)
  - Pokéball icons as dividers
```

---

### ✅ SECTION 9: Feature Comparison Table
**Height:** 600px
**Type:** Competitive comparison (if needed)

```html
<table>
  <thead>
    <tr>
      <th>Feature</th>
      <th>PokeRarityScanner</th>
      <th>Manual Check</th>
      <th>Other Apps</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Speed</td>
      <td>✅ <2s</td>
      <td>❌ 5min+</td>
      <td>⚠️ 30s</td>
    </tr>
    <tr>
      <td>Accuracy</td>
      <td>✅ 99%</td>
      <td>⚠️ 70%</td>
      <td>⚠️ 85%</td>
    </tr>
    <!-- daha fazla row -->
  </tbody>
</table>
```

---

### ✅ SECTION 10: FAQ ("Common Questions")
**Height:** 800px
**Type:** Accordion

**Questions:**

```
Q: What Pokemon does the app support?
A: Generasi 1-9, terbaru Pokemon Legends.

Q: Does it work without internet?
A: Offline mode for scanning. Cloud sync optional.

Q: Is my data private?
A: 100% encrypted. No data sold. Open privacy policy.

Q: Which phones are supported?
A: Android 13+ (API 26+). iPad support coming Q2 2026.

Q: How is the rarity score calculated?
A: 15+ factors: CP, IV%, date, variant, type, market trends...

Q: Can I export my scans?
A: Yes! CSV, JSON, PDF formats available.

Q: How often is data updated?
A: Real-time Gen 4-9 data. Updated daily with new research.

Q: Is there a desktop version?
A: Web dashboard in development. Early 2026.

Q: What's the subscription model?
A: Free forever for basic scans. Premium (Pro) for 10K+ scans/year.

Q: How can I report bugs?
A: In-app feedback or support@pokerarity.com
```

**Accordion Design:**
```jsx
.faq-item {
  border-bottom: 1px solid #E0E0E0
  padding: 20px 0
  
  .question {
    font-weight: 600
    cursor: pointer
    display: flex
    justify-content: space-between
    align-items: center
    
    &:hover { color: #E3350D }
  }
  
  .answer {
    max-height: 0
    overflow: hidden
    transition: max-height 0.3s ease
    color: #666
    margin-top: 12px
    
    &.open { max-height: 500px }
  }
}
```

---

### ✅ SECTION 11: Download Section ("Get Started Now")
**Height:** 400px
**Type:** Promotional CTA

```
Headline: "150K+ Trainers are already scanning"
Subheading: "Join the fastest-growing Pokemon rarity analysis community"

CTA Buttons (Side by side):
  [🚀 Download on Google Play]  [🍎 Download on App Store]
  
  Coming Soon:
  [💻 Web Dashboard (Early 2026)]
  [🖥️ Desktop App (Q2 2026)]

Social proof:
  "⭐⭐⭐⭐⭐ 4.8/5 rating"
  "99% accurate • 2M+ scans • 24h support"
```

**Design:**
```jsx
.download-section {
  background: linear-gradient(135deg, #E3350D, #FF6B6B)
  color: white
  padding: 80px 40px
  border-radius: 24px
  text-align: center
  
  .download-buttons {
    display: flex
    gap: 20px
    justify-content: center
    margin-top: 40px
    flex-wrap: wrap
    
    .btn {
      display: flex
      align-items: center
      gap: 12px
      padding: 16px 32px
      background: white
      color: #E3350D
      border-radius: 12px
      font-weight: 600
      text-decoration: none
      transition: transform 0.3s, box-shadow 0.3s
      
      &:hover {
        transform: translateY(-4px)
        box-shadow: 0 8px 24px rgba(0,0,0,0.2)
      }
    }
  }
}
```

---

### ✅ SECTION 12: Footer
**Height:** 400px
**Type:** Comprehensive footer (Jeton style)

**Layout:** 4-column + copyright

**Column 1: Brand**
```
Logo + "PokeRarityScanner v1.0"
"Helping trainers find rare Pokemon since 2026"
Social icons: Instagram | Twitter | Discord | YouTube
```

**Column 2: Discover**
```
• Features
• How It Works
• Pricing (Coming Soon)
• Changelog
• Release Notes
```

**Column 3: Company**
```
• About Us
• Blog
• Press Kit
• Partnerships
• Careers (Hiring!)
```

**Column 4: Legal & Support**
```
• Privacy Policy
• Terms of Service
• Cookie Policy
• Support / FAQ
• Report a Bug
• Contact: support@pokerarity.com
```

**Copyright:**
```
© 2026 PokeRarityScanner. Not affiliated with Pokemon/Niantic.
All Pokemon © Game Freak / Nintendo / The Pokémon Company
Made with ❤️ by [Your Name/Studio]
```

---

## 🛠️ IMPLEMENTATION PLAN (Next.js + Tailwind)

### **PHASE 1: Project Setup (30 min)**
```bash
npx create-next-app@latest pokerarity-web \
  --typescript \
  --tailwind \
  --eslint \
  --app

npm install framer-motion axios zustand
```

**Project Structure:**
```
pokerarity-web/
├── src/
│   ├── app/
│   │   ├── layout.tsx
│   │   ├── page.tsx
│   │   └── globals.css
│   ├── components/
│   │   ├── Header.tsx
│   │   ├── Hero.tsx
│   │   ├── ValueProp.tsx
│   │   ├── FeaturesGrid.tsx
│   │   ├── HowItWorks.tsx
│   │   ├── Showcase.tsx
│   │   ├── Testimonials.tsx
│   │   ├── Stats.tsx
│   │   ├── FAQ.tsx
│   │   ├── Download.tsx
│   │   ├── Footer.tsx
│   │   └── Navigation.tsx
│   ├── styles/
│   │   ├── animations.css
│   │   └── variables.css
│   └── data/
│       ├── testimonials.ts
│       └── faq.ts
├── public/
│   ├── images/
│   ├── videos/
│   └── icons/
└── tailwind.config.ts
```

---

### **PHASE 2: Component Creation (2-3 hours)**

#### 1. **Header/Navigation**
```tsx
// src/components/Header.tsx
- Sticky navbar
- Logo + navigation links
- Download button
- Mobile hamburger menu
- Language switcher
```

#### 2. **Hero Section**
```tsx
// src/components/Hero.tsx
- Full-screen background
- Animated gradient
- Floating Pokéball animations
- CTA buttons
- Device mockup image
```

#### 3. **Features Grid**
```tsx
// src/components/FeaturesGrid.tsx
- 4 feature cards (Scan, Analyze, Score, Organize)
- Icons + descriptions
- Hover animations
- Responsive 1x4 → 2x2 → 1x1
```

#### 4. **How It Works**
```tsx
// src/components/HowItWorks.tsx
- 5 step timeline
- Animated step numbers
- Connecting lines
- Responsive carousel for mobile
- Step descriptions from data
```

#### 5. **Testimonials**
```tsx
// src/components/Testimonials.tsx
- Carousel component (Swiper or Embla)
- 5 testimonial cards
- Star ratings
- Tags
- Auto-rotate + manual controls
```

#### 6. **FAQ Accordion**
```tsx
// src/components/FAQ.tsx
- Accordion component
- Smooth open/close animations
- Search filter
- Categories
```

#### 7. **Footer**
```tsx
// src/components/Footer.tsx
- 4-column layout
- Links + copyright
- Social media icons
- Newsletter signup
```

---

### **PHASE 3: Styling & Animations (1-2 hours)**

**Tailwind Config:**
```js
// tailwind.config.ts
module.exports = {
  theme: {
    extend: {
      colors: {
        pokemon: {
          red: '#E3350D',
          black: '#000000',
          yellow: '#FFDE00',
          gray: '#F5F5F5',
          dark: '#1A1A1A',
        }
      },
      animation: {
        float: 'float 6s ease-in-out infinite',
        pulse: 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        slideIn: 'slideIn 0.5s ease-out',
      },
      keyframes: {
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-20px)' },
        },
        slideIn: {
          '0%': { transform: 'translateX(-100%)', opacity: 0 },
          '100%': { transform: 'translateX(0)', opacity: 1 },
        }
      }
    }
  }
}
```

**Framer Motion Animations:**
- Staggered animations on component mount
- Scroll-triggered animations (useInView)
- Parallax scrolling on hero
- Card hover effects
- Counter animations for stats

---

### **PHASE 4: Data & Content (1 hour)**

**src/data/testimonials.ts:**
```ts
export const testimonials = [
  {
    id: 1,
    name: "Ash M.",
    role: "Pokemon Collector",
    avatar: "/avatars/ash.jpg",
    quote: "Instantly know which Pokemon to keep. Game changer!",
    rating: 5,
    tag: "Recommended"
  },
  // ... 4 more testimonials
]
```

**src/data/faq.ts:**
```ts
export const faqItems = [
  {
    id: 1,
    question: "What Pokemon does the app support?",
    answer: "Generation 1-9, including latest Pokemon Legends...",
    category: "features"
  },
  // ... 9 more FAQs
]
```

---

### **PHASE 5: SEO & Meta Tags (30 min)**

```tsx
// src/app/layout.tsx
export const metadata: Metadata = {
  title: "PokeRarityScanner - Instant Pokemon Rarity Analysis",
  description: "Scan Pokemon cards in real-time. Get nadirlik scores instantly. The fastest way to find your rarest Pokemon.",
  keywords: "Pokemon GO, rarity calculator, Pokemon scanner, IV calculator, shiny detection",
  openGraph: {
    title: "PokeRarityScanner",
    description: "Discover your rarest Pokemon instantly",
    images: ['/og-image.jpg'],
  }
}
```

---

### **PHASE 6: Testing & Optimization (1 hour)**

```
✅ Mobile responsiveness (iOS/Android)
✅ Performance (Lighthouse 90+)
✅ SEO (Google Search Console)
✅ Accessibility (a11y)
✅ Cross-browser testing
✅ Loading speed optimization
✅ Image optimization (next/image)
```

---

### **PHASE 7: Deployment (15 min)**

```bash
# Deploy to Vercel (easiest for Next.js)
npm install -g vercel
vercel

# OR deploy to Netlify
npm run build
netlify deploy --prod --dir=.next

# OR self-host
npm run build
npm start
```

---

## 🚀 EXECUTION ROADMAP (Timeline)

| Phase | Task | Duration | Status |
|-------|------|----------|--------|
| 1 | Project setup + dependencies | 30 min | 📋 Planned |
| 2 | Header + Hero components | 45 min | 📋 Planned |
| 3 | Features + How It Works | 45 min | 📋 Planned |
| 4 | Testimonials + Social Proof | 30 min | 📋 Planned |
| 5 | FAQ + Download sections | 30 min | 📋 Planned |
| 6 | Footer + global styling | 30 min | 📋 Planned |
| 7 | Animations + microinteractions | 60 min | 📋 Planned |
| 8 | Responsive tweaks + mobile | 45 min | 📋 Planned |
| 9 | SEO + meta tags | 30 min | 📋 Planned |
| 10 | Performance optimization | 30 min | 📋 Planned |
| 11 | Testing + QA | 45 min | 📋 Planned |
| 12 | Deploy to production | 15 min | 📋 Planned |
| **TOTAL** | | **~6.5 hours** | |

---

## 📦 DELIVERABLES

✅ **Responsive web sitesi** (Mobile First)
✅ **Modern design** (Jeton.com stili)
✅ **Fast performance** (Lighthouse 90+)
✅ **SEO optimized**
✅ **Smooth animations** (Framer Motion)
✅ **Download links** (Play Store, App Store)
✅ **Mobile-ready** (1080px → 320px)
✅ **Accessibility** (WCAG 2.1 AAA)
✅ **Dark mode ready** (Tailwind support)

---

## 🎯 SUCCESS METRICS

- [ ] Lighthouse score: 90+
- [ ] Mobile score: 95+
- [ ] Page load time: <2s
- [ ] CLS (Cumulative Layout Shift): <0.1
- [ ] Mobile conversions: Track download clicks
- [ ] SEO rankings: Track keyword positions
- [ ] User engagement: Track scroll depth

---

## 💡 NOTES FOR AJANAA

**Bu briefe ajan verirken şöyle söyle:**

```
Bana PokeRarityScanner Android uygulaması için profesyonel bir web sitesi yap.

PATTERN: Jeton.com'un layout ve UX'ini takip et (ben pattern'i analiz ettim)

STACK: 
- Next.js 14 + TypeScript
- Tailwind CSS
- Framer Motion (animations)
- Responsive Mobile-First

BÖLÜMLER (Jeton pattern'i):
1. Sticky Header/Nav
2. Hero Section (Full screen)
3. Main Value Prop ("Unify Your Pokemon Knowledge")
4. Features Grid (4 features: Scan, Analyze, Score, Organize)
5. How It Works (5-step timeline)
6. Feature Showcase (Rarity scoring, visual detection)
7. Testimonials (5 user reviews)
8. Statistics (150K users, 2.5M scans, 99% accuracy)
9. FAQ (10 questions)
10. Download CTA
11. Footer

DESIGN:
- Renk: Pokemon Red (#E3350D), Yellow (#FFDE00), Black
- Fonts: Montserrat (headings), Inter (body)
- Animations: Floating Pokéballs, smooth transitions, scroll effects
- Responsive: 320px → 1920px

TAMAMLADIKTAN SONRA:
- Deploy to Vercel for free
- SEO optimize
- Test mobile responsiveness

[BU BRIEF'İ YAPIŞTUR - TOO DETAILED BELOW IS BETTER FOR AGENT CONSUMPTION]
```

---

> 🚀 **READY TO BUILD!**
>
> Bu sistem az sayıda adımla yapılabilecek modern, hızlı, responsive, profesyonel bir web sitesi oluşturacak. 
> Jeton'un minimalist ama powerful tasarımını Pokemon temasıyla birleştircek.
>
> **Ajanaa bu dosyanın tamamını ver, o da hemen başa başlayacak!** 💪
