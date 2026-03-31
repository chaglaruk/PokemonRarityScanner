# 🚀 PokeRarityScanner Website - Deployment Guide

Your website is ready to deploy in 5 minutes!

## 🟢 Quickest Option: Vercel (Recommended)

Vercel is the creator of Next.js, so deployment is seamless and free.

### Step 1: Create Vercel Account
- Go to [vercel.com](https://vercel.com)
- Sign up with GitHub, GitLab, or Bitbucket

### Step 2: Deploy
Option A - Import repository:
1. Click "New Project"
2. Connect your Git repository
3. Select the `website` folder
4. Click "Deploy"

Option B - CLI:
```bash
npm install -g vercel
cd website
vercel
# Follow prompts, choose workspace and confirm deployment
```

✅ **Done!** Your site is live at `your-project.vercel.app`

---

## 🌐 Alternative Option: Netlify

### Step 1: Create Account
- Go to [netlify.com](https://netlify.com)
- Sign up with GitHub

### Step 2: Deploy Static Site
- Drag and drop the `website/out/` folder, or
- Connect your GitHub repository

### Step 3: Build Settings
```
Build command: npm run build
Publish directory: out
```

✅ **Done!** Your site is live at Netlify's subdomain

---

## 🐙 GitHub Pages Option

### Step 1: Update next.config.js
```javascript
const nextConfig = {
  output: 'export',
  basePath: '/PokeRarityScanner',  // if using project site
  images: { unoptimized: true }
}
```

### Step 2: Build and Commit
```bash
npm run build
git add out/
git commit -m "Update static build"
git push origin main
```

### Step 3: Enable Pages
- Go to Settings → Pages
- Source: Deploy from a branch
- Branch: `main`
- Folder: `/out`

✅ **Done!** Your site is live at `username.github.io/PokeRarityScanner`

---

## 🐳 Docker / Self-Hosted Deployment

### Create Dockerfile
```dockerfile
FROM node:18-alpine

WORKDIR /app

# Copy files
COPY package*.json ./
COPY . .

# Install and build
RUN npm ci
RUN npm run build

# Remove dev dependencies
RUN npm prune --omit=dev

EXPOSE 3000

CMD ["npm", "start"]
```

### Build & Run
```bash
docker build -t pokerarityscanner-website .
docker run -p 3000:3000 pokerarityscanner-website
```

---

## 📋 Pre-Deployment Checklist

- [ ] Update `.next/telemetry` setting if desired
- [ ] Review SEO metadata in `app/layout.tsx`
- [ ] Update Open Graph images
- [ ] Add Google Analytics ID
- [ ] Replace placeholder links in Footer  
- [ ] Add real download links for Google Play
- [ ] Update contact email in footer
- [ ] Test on mobile (responsive)
- [ ] Test animations in Firefox/Safari
- [ ] Run Lighthouse audit
- [ ] Set up error monitoring (Sentry recommended)

---

## 🔧 Environment Variables

If needed, create `.env.local`:
```
NEXT_PUBLIC_APP_ID=your-app-id
NEXT_PUBLIC_ANALYTICS_ID=your-analytics-id
```

Reference in components:
```tsx
const APP_ID = process.env.NEXT_PUBLIC_APP_ID
```

---

## 📊 Monitoring & Analytics

### Add Google Analytics
```tsx
// In app/layout.tsx
<Script 
  src="https://www.googletagmanager.com/gtag/js?id=GA_MEASUREMENT_ID"
  strategy="afterInteractive"
/>
<Script id="google-analytics" strategy="afterInteractive">
  {`window.dataLayer = window.dataLayer || [];
    function gtag(){dataLayer.push(arguments);}
    gtag('js', new Date());
    gtag('config', 'GA_MEASUREMENT_ID');</script>`}
</Script>
```

### Lighthouse Audit
```bash
# After deployment, run:
npm install -g lighthouse
lighthouse https://your-site.com --view
```

---

## 🎯 Custom Domain

### Vercel
1. Go to Project Settings → Domains
2. Add your domain
3. Update DNS records (instructions provided)

### Netlify
1. Domain Settings → Add domain
2. Update DNS or point to Netlify nameservers

### GitHub Pages
1. Add CNAME file to `public/` with your domain
2. Update repo settings → Pages → Custom domain

---

## 🔒 SSL/HTTPS

- ✅ Vercel: Automatic (free)
- ✅ Netlify: Automatic (free)
- ✅ GitHub Pages: Automatic (free)
- ✅ Self-hosted: Use Let's Encrypt (free) or Cloudflare

---

## 🚀 Continuous Deployment

### Auto-deploy on Push
**Vercel & Netlify**: Automatic with Git integration
**GitHub Pages**: Push to main branch triggers CI/CD

### Create GitHub Actions Workflow
Add `.github/workflows/deploy.yml`:
```yaml
name: Deploy

on:
  push:
    branches: [ main ]

jobs:
  build_and_deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      - run: npm ci
      - run: npm run build
      - name: Deploy to Vercel
        uses: vercel/action@master
        with:
          vercel-token: ${{ secrets.VERCEL_TOKEN }}
```

---

## ⚡ Performance Tips

1. **Minify images** - Use optimized PNGs/WebPs
2. **Enable GZIP compression** - Most hosts do automatically
3. **Use CDN** - Vercel/Netlify include CDN
4. **Cache headers** - Set in `next.config.js` or server config
5. **Lazy load sections** - Already implemented with `whileInView`

---

## 🆘 Troubleshooting

| Problem | Solution |
|---------|----------|
| Fonts not loading | Check Google Fonts CDN in `globals.css` |
| Images not showing | Use absolute paths, check `public/` folder |
| Animations stuttering | Reduce complexity, use `GPU: transform` CSS |
| Build fails | Run `npm install` again, clear cache |
| 404 errors | Check link references in components |

---

## 📞 Support

- Next.js Docs: [nextjs.org/docs](https://nextjs.org/docs)
- Tailwind CSS: [tailwindcss.com/docs](https://tailwindcss.com/docs)
- Framer Motion: [framer.com/motion](https://framer.com/motion)

**Recommended**: Deploy on Vercel for best performance with Next.js! 🎯
