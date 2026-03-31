const sharp = require('sharp');

// Create instruction image 
const createInstructionPNG = async () => {
  const width = 1200;
  const height = 3200;

  // Create SVG with detailed instructions
  const svgImage = `<svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
    <rect width="${width}" height="${height}" fill="white"/>
    <rect width="${width}" height="100" fill="#E3350D"/>
    <text x="40" y="70" font-size="44" font-weight="bold" fill="white" font-family="Arial">PokeRarityScanner Website</text>
    
    <text x="40" y="150" font-size="28" font-weight="bold" fill="#E3350D" font-family="Arial">Build Instructions</text>
    
    <text x="40" y="200" font-size="13" fill="#333" font-family="Arial">
      <tspan x="40" dy="18">STACK: Next.js 14 + TypeScript + Tailwind CSS + Framer Motion</tspan>
      <tspan x="40" dy="28" font-weight="bold" fill="#E3350D">11 WEBSITE SECTIONS:</tspan>
      <tspan x="60" dy="18">1. Header/Nav - Sticky navbar with logo and download button</tspan>
      <tspan x="60" dy="18">2. Hero - Full-screen "Scan once. Know everything"</tspan>
      <tspan x="60" dy="18">3. Value Prop - "Unify your Pokemon knowledge"</tspan>
      <tspan x="60" dy="18">4. Features Grid - Scan, Analyze, Score, Organize (4 cards)</tspan>
      <tspan x="60" dy="18">5. How It Works - 5-step timeline</tspan>
      <tspan x="60" dy="18">6. Showcase - Rarity scoring, visual detection, battle insights</tspan>
      <tspan x="60" dy="18">7. Testimonials - 5 trainer reviews carousel</tspan>
      <tspan x="60" dy="18">8. Statistics - 150K+ users, 2.5M+ scans, 99% accuracy</tspan>
      <tspan x="60" dy="18">9. FAQ - 10 questions with accordion animations</tspan>
      <tspan x="60" dy="18">10. Download CTA - Google Play and App Store buttons</tspan>
      <tspan x="60" dy="18">11. Footer - Links, legal, social media icons</tspan>
      
      <tspan x="40" dy="28" font-weight="bold" fill="#E3350D">DESIGN COLORS:</tspan>
      <tspan x="60" dy="18">Primary Red: #E3350D (Pokéball)</tspan>
      <tspan x="60" dy="18">Secondary Black: #000000</tspan>
      <tspan x="60" dy="18">Accent Yellow: #FFDE00 (Pikachu)</tspan>
      <tspan x="60" dy="18">Background Gray: #F5F5F5</tspan>
      
      <tspan x="40" dy="28" font-weight="bold" fill="#E3350D">IMPLEMENTATION TIMELINE:</tspan>
      <tspan x="60" dy="18">Phase 1: Setup Next.js Project (15 min)</tspan>
      <tspan x="60" dy="18">Phase 2: Create Component Structure (30 min)</tspan>
      <tspan x="60" dy="18">Phase 3: Build All 11 Components (2.5 hours)</tspan>
      <tspan x="60" dy="18">Phase 4: Add Framer Motion Animations (60 min)</tspan>
      <tspan x="60" dy="18">Phase 5: Styling and Tailwind Config (45 min)</tspan>
      <tspan x="60" dy="18">Phase 6: Responsive Tweaks and Mobile Testing (45 min)</tspan>
      <tspan x="60" dy="18">Phase 7: Performance Optimization (30 min)</tspan>
      <tspan x="60" dy="18">Phase 8: Deploy to Vercel (15 min)</tspan>
      <tspan x="60" dy="18" font-weight="bold">TOTAL: 6.5 HOURS</tspan>
      
      <tspan x="40" dy="28" font-weight="bold" fill="#E3350D">SUCCESS CRITERIA:</tspan>
      <tspan x="60" dy="18">✓ Responsive design (320px - 1920px)</tspan>
      <tspan x="60" dy="18">✓ Smooth animations and transitions</tspan>
      <tspan x="60" dy="18">✓ Lighthouse performance score 90+</tspan>
      <tspan x="60" dy="18">✓ Mobile performance score 95+</tspan>
      <tspan x="60" dy="18">✓ Page load time under 3 seconds</tspan>
      <tspan x="60" dy="18">✓ No console errors</tspan>
      <tspan x="60" dy="18">✓ Deployed and live on Vercel</tspan>
      
      <tspan x="40" dy="28" font-weight="bold" fill="#E3350D">QUICK START:</tspan>
      <tspan x="60" dy="18">$ npx create-next-app@latest pokerarity-web --typescript --tailwind</tspan>
      <tspan x="60" dy="18">$ npm install framer-motion react-icons</tspan>
      <tspan x="60" dy="18">$ npm run dev</tspan>
      <tspan x="60" dy="18">$ npm run build</tspan>
      <tspan x="60" dy="18">$ npx vercel deploy</tspan>
      
      <tspan x="40" dy="28" font-size="11" fill="#999">For complete details, see AGENT_INSTRUCTION_WEBSITE.pdf</tspan>
      <tspan x="40" dy="18" font-size="11" fill="#999">Generated: March 20, 2026</tspan>
    </text>
  </svg>`;

  try {
    await sharp(Buffer.from(svgImage))
      .png()
      .toFile('AGENT_INSTRUCTION_WEBSITE_VISUAL.png');
    
    console.log('✓ Successfully created AGENT_INSTRUCTION_WEBSITE_VISUAL.png');
  } catch (error) {
    console.error('Error creating image:', error.message);
  }
};

createInstructionPNG();
