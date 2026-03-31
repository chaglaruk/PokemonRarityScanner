# 🚀 PokeRarityScanner Web Sitesi — Ajan Execution Instruction

> **Hedef:** Jeton.com stili, modern, responsive, Next.js web sitesi oluştur  
> **Zaman:** ~6-7 saat full build  
> **Sonuç:** Production-ready site, Vercel'de deploy  

---

## 📋 INSTRUCTION FOR AGENT (Copy & Paste to Agent)

```
===============================================
TASK: Build PokeRarityScanner Website
===============================================

BUILD A PROFESSIONAL WEB PORTAL FOR POKERARITY SCANNER APP
- Visual Template: Jeton.com (clean, modern, conversion-focused)
- Technology Stack: Next.js 14 + TypeScript + Tailwind CSS + Framer Motion
- Design: Mobile-first, responsive, Pokémon-themed
- Target: Android Pokemon rarity analysis app

APP SUMMARY:
PokeRarityScanner is an Android app that scans Pokémon GO cards via OCR,
analyzes CP/HP/IV/variants (Shiny/Shadow/Lucky), and calculates a 1-100 
rarity score instantly. 150K+ users scan 2.5M+ Pokemon with 99% accuracy.

WEBSITE STRUCTURE (11 SECTIONS):

1. HEADER/NAV (Sticky, 80px)
   - Logo + PokeRarityScanner text
   - Nav: Home | Features | How It Works | Download | FAQ | Contact
   - CTA Button: "Download Now" (Red, primary)
   - Language: EN | TR

2. HERO (Full screen, 100vh)
   - Headline: "Scan once. Know everything."
   - Subheading: "The fastest way to discover your rarest Pokemon"
   - Background: Animated gradient (Red → Light Red) + floating Pokéball
   - CTA Buttons: "Download Now" (Primary) + "Learn More" (Secondary)
   - Device mockup showing app scanning
   - Social proof: "Trusted by 150K+ Pokemon trainers"

3. VALUE PROP SECTION (600px)
   - Title: "Unify your Pokemon knowledge"
   - 2-column layout (image left, text right)
   - Left: Animated mockup of app scanning sequence
   - Right: Features list (✓ Instant rarity, ✓ CP/HP detection, ✓ Shiny recognition)
   - Background: Light gray (#F5F5F5)

4. FEATURES GRID (700px)
   - 4 feature cards in 2×2 grid (responsive 1×4 → 2×2 → 1×1)
   - Card 1: "Scan" - Pokéball icon, description, GIF
   - Card 2: "Analyze" - Chart icon, OCR/AI details
   - Card 3: "Score" - Star icon, rarity gauge animation (0→92)
   - Card 4: "Organize" - List icon, scan history
   - Hover effects + subtle shadows

5. HOW IT WORKS (800px)
   - Vertical timeline or horizontal carousel
   - 5 steps: Start → Open Pokemon → Tap Pokéball → Get Results → See Score
   - Step circles with numbers (red background)
   - Connecting lines between steps
   - Step descriptions + visuals

6. FEATURE SHOWCASE (900px)
   - Subsection A: Real-Time Rarity Analysis (left/right alternating)
     * Left: Animated Pokemon card with OCR boxes
     * Right: 7 rarity factors listed
   - Subsection B: Smart Visual Detection
     * 4 detection types: Shiny, Shadow, Lucky, Regional
     * Gallery with examples
   - Subsection C: Battle-Ready Insights
     * League recommendations + type advantages

7. TESTIMONIALS (700px)
   - 5 user testimonial cards
   - Carousel (auto-rotate + manual controls)
   - Rating stars + name + role
   - Tags: "Recommended", "Game Changer", "Family Friendly", etc.
   - Data: Include real-looking trainer quotes

8. STATISTICS (500px)
   - 4 metric boxes:
     * +150K Active Users
     * +2.5M Pokemon Scanned
     * 99% Accuracy Rate
     * <2s Scan Speed
   - Animated counters (0 → final number)
   - Pokéball dividers

9. FAQ ACCORDIONS (800px)
   - 10 FAQs with smooth open/close animations
   - Default data:
     1. "What Pokemon does the app support?"
     2. "Does it work offline?"
     3. "Is my data private?"
     4. "Which phones are supported?"
     5. "How is the rarity score calculated?"
     6. "Can I export my scans?"
     7. "How often is data updated?"
     8. "Is there a desktop version?"
     9. "What's the subscription model?"
     10. "How can I report bugs?"
   - Search/filter capability

10. DOWNLOAD CTA (400px)
    - Headline: "150K+ Trainers are already scanning"
    - Subheading: "Join the fastest-growing Pokemon rarity community"
    - Buttons: [Google Play] [App Store]
    - Coming Soon: Web Dashboard, Desktop App
    - Background: Red gradient

11. FOOTER (400px)
    - 4 columns: Brand | Discover | Company | Legal & Support
    - Social icons: Instagram, Twitter, Discord, YouTube
    - Links: Terms, Privacy, Support, Contact
    - Copyright + "Made with ❤️"

DESIGN REQUIREMENTS:

Colors:
- Primary: #E3350D (Pokémon Red)
- Secondary: #000000 (Black)
- Accent: #FFDE00 (Pikachu Yellow)
- Background: #F5F5F5 (Light Gray)
- Dark Text: #1A1A1A
- Success: #4CAF50 (Green)

Typography:
- Headings: Montserrat Bold (weights: 700, 800)
- Body: Inter Regular (weights: 400, 500)
- Mono: JetBrains Mono (for code)

Animations:
- Floating Pokéballs (infinite float animation)
- Smooth scroll transitions
- Staggered component animations
- Hover effects on cards
- Animated counter numbers
- Parallax on hero
- Accordion smooth open/close

Responsiveness:
- Mobile: 320px - 640px
- Tablet: 641px - 1024px
- Desktop: 1025px - 1920px
- Max-width: 1400px

IMPLEMENTATION STEPS:

Step 1: Create Next.js Project
```bash
npx create-next-app@latest pokerarity-web \
  --typescript \
  --tailwind \
  --eslint \
  --app

npm install framer-motion react-icons
```

Step 2: Create Component Structure
- src/components/Header.tsx (sticky nav)
- src/components/Hero.tsx (hero section)
- src/components/ValueProp.tsx
- src/components/FeaturesGrid.tsx
- src/components/HowItWorks.tsx
- src/components/Showcase.tsx
- src/components/Testimonials.tsx
- src/components/Stats.tsx
- src/components/FAQ.tsx
- src/components/Download.tsx
- src/components/Footer.tsx

Step 3: Create Data Files
- src/data/testimonials.ts (5 testimonial objects)
- src/data/faq.ts (10 FAQ objects)

Step 4: Configure Tailwind
- Custom colors (Pokemon red, yellow, black)
- Custom animations (float, slideIn, fadeIn)
- Custom fonts (Montserrat, Inter)

Step 5: Build Components with Framer Motion
- Use staggerContainer for sequential animations
- Use ScrollAnimationTrigger for on-scroll animations
- Add hover animations to interactive elements

Step 6: Styling & Responsive Design
- Mobile-first approach
- Test on 320px, 768px, 1920px
- Use Tailwind responsive prefixes (sm:, md:, lg:, xl:)

Step 7: SEO Optimization
- Meta tags (title, description, keywords, og:image)
- Structured data (JSON-LD for schema)
- Sitemap.xml
- robots.txt

Step 8: Performance Optimization
- Use Next.js Image component for images
- Lazy load components
- Optimize bundle size
- Minify CSS/JS

Step 9: Testing
- Responsive design test
- Cross-browser testing
- Lighthouse audit (target 90+)
- Accessibility check (a11y)

Step 10: Deployment
- Deploy to Vercel (npx vercel deploy)
- OR deploy to Netlify (npm run build && netlify deploy)
- Set up custom domain
- Enable HTTPS
- Configure analytics

CONTENT GUIDELINES:

- Use casual but professional tone
- Include Pokemon references (Ash, Misty, Brock, etc.)
- Emphasize speed, accuracy, ease-of-use
- Include real-looking stats (150K+ users is aspirational)
- Make it fun but credible

DESIGN INSPIRATION:
- Jeton.com (layout, spacing, card design)
- Pokémon branding (colors, personality)
- Modern SaaS sites (clean, fast, conversion-focused)

DELIVERABLES:
✅ Responsive web portal
✅ 11 sections with smooth animations
✅ Mobile-optimized (tested on multiple sizes)
✅ SEO-ready
✅ Performance optimized (Lighthouse 90+)
✅ Deployed to production
✅ Download buttons linking to app stores
✅ FAQ with search capability

DO NOT:
- Use copyrighted Pokemon artwork (use official assets or icons only)
- Make false accuracy claims
- Include affiliate links
- Track user data without consent

FINAL CHECKLIST:
□ All sections built and styled
□ Animations working smoothly
□ Mobile responsiveness verified
□ Images optimized
□ SEO tags in place
□ Performance tested
□ Deployed to production
□ Download links working
□ FAQ searchable
□ Footer links functional

START IMMEDIATELY. BUILD FULL WEBSITE IN ONE EXECUTION.
```

---

## 🔧 PRE-EXECUTION CHECKLIST

Ajan başlamadan önce kontrol et:

- [ ] Node.js 18+ yüklü mü? (`node --version`)
- [ ] npm yüklü mü? (`npm --version`)
- [ ] Vercel account'u var mı? (deployment için)
- [ ] GitHub repo hazırlandı mı? (opsiyonel ama tavsiye edilir)

---

## 📊 EXECUTION PHASES & TIMELINE

| # | Phase | Task | Estimated Time | Status |
|---|-------|------|-----------------|--------|
| 1 | Setup | Create Next.js project + dependencies | 15 min | ⏳ |
| 2 | Structure | Create folder structure + data files | 15 min | ⏳ |
| 3 | Build | Header + Hero + ValueProp | 45 min | ⏳ |
| 4 | Build | FeaturesGrid + HowItWorks | 45 min | ⏳ |
| 5 | Build | Showcase + Testimonials + Stats | 45 min | ⏳ |
| 6 | Build | FAQ + Download + Footer | 30 min | ⏳ |
| 7 | Style | Animations + Tailwind customization | 60 min | ⏳ |
| 8 | Polish | Responsive tweaks + mobile testing | 45 min | ⏳ |
| 9 | Optimize | SEO + performance tuning | 30 min | ⏳ |
| 10 | Test | Cross-browser + accessibility | 30 min | ⏳ |
| 11 | Deploy | Vercel/Netlify deployment | 15 min | ⏳ |
| **TOTAL** | | | **~6.5 hours** | |

---

## 🎯 SUCCESS CRITERIA

✅ Website is fully responsive (320px to 1920px)
✅ All animations are smooth and performant
✅ Page load time < 3 seconds
✅ Lighthouse score > 90
✅ Mobile score > 95
✅ No console errors
✅ All links functional
✅ SEO meta tags present
✅ Deployed and live

---

## 💬 AFTER AGENT COMPLETES WORK

**If successful:**
- ✅ Site is live and accessible
- ✅ All sections working
- ✅ Mobile responsive
- ✅ Fast and optimized
- ✅ Ready for production

**If issues arise:**
- Check Lighthouse report for performance bottlenecks
- Verify mobile responsiveness on actual devices
- Check console for JavaScript errors
- Test on Chrome, Firefox, Safari

---

## 🚀 NEXT STEPS AFTER WEBSITE LAUNCH

1. **Analytics Setup**
   - Google Analytics 4
   - Hotjar (heatmaps, user recordings)
   - Monitor download button clicks

2. **Download Links**
   - Add Google Play Store link (when published)
   - Add Apple App Store link (when published)

3. **Marketing**
   - Social media promotion
   - SEO optimization (backlinks, content)
   - Community engagement (Reddit, Discord)

4. **Future Enhancements**
   - Web dashboard (v1.2)
   - Blog section
   - Video tutorials
   - API documentation (for developers)

---

> 💡 **TIP:** Ajanaa bu INSTRUCTION'ı direkt olarak ver. Diğer iki dosyayı reference olarak kullan ama bu instruction'ı primary olarak istifade et. Çünkü bu detaylı ve step-by-step.

