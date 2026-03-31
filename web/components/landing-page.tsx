"use client";

import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import {
  ArrowRight,
  Download,
  Globe,
  Layers3,
  Sparkles,
  Star,
  TimerReset,
  Zap,
} from "lucide-react";

import {
  defaultLocale,
  landingPageContent,
  localeLabels,
  type SupportedLocale,
} from "@/components/content/site-content";
import { Logo } from "@/components/logo";
import {
  detectBrowserLocale,
  isRtlLocale,
  readStoredLocale,
  writeStoredLocale,
} from "@/lib/locale";

const fadeUp = {
  initial: { opacity: 0, y: 24 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, amount: 0.15 },
  transition: { duration: 0.5, ease: [0.22, 1, 0.36, 1] as const },
};

function SectionBadge({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-4 py-2 text-xs font-semibold uppercase tracking-[0.28em] text-pokeyellow">
      <Zap className="h-4 w-4" />
      {children}
    </span>
  );
}

function SectionHeader({
  eyebrow,
  title,
  copy,
}: {
  eyebrow: string;
  title: string;
  copy?: string;
}) {
  return (
    <motion.div {...fadeUp}>
      <SectionBadge>{eyebrow}</SectionBadge>
      <h2 className="section-title mt-5">{title}</h2>
      {copy ? <p className="section-copy">{copy}</p> : null}
    </motion.div>
  );
}

function LocaleSelector({
  locale,
  onChange,
}: {
  locale: SupportedLocale;
  onChange: (locale: SupportedLocale) => void;
}) {
  return (
    <label className="inline-flex items-center gap-3 rounded-full border border-white/10 bg-black/20 px-3 py-2 text-sm text-slate-200">
      <Globe className="h-4 w-4 text-pokeyellow" />
      <span className="sr-only">Language selector</span>
      <select
        aria-label="Language selector"
        value={locale}
        onChange={(event) => onChange(event.target.value as SupportedLocale)}
        className="bg-transparent text-sm font-semibold text-white outline-none"
      >
        {Object.entries(localeLabels).map(([value, label]) => (
          <option key={value} value={value} className="bg-zinc-950">
            {label}
          </option>
        ))}
      </select>
    </label>
  );
}

function FeatureGlyph({ icon }: { icon: "scan" | "analyze" | "score" }) {
  if (icon === "analyze") {
    return <Layers3 className="h-6 w-6" />;
  }

  if (icon === "score") {
    return <Star className="h-6 w-6" />;
  }

  return <Sparkles className="h-6 w-6" />;
}

export default function LandingPage() {
  const [locale, setLocale] = useState<SupportedLocale>(defaultLocale);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setHydrated(true);

    const storedLocale = readStoredLocale();
    if (storedLocale) {
      setLocale(storedLocale);
      return;
    }

    setLocale(detectBrowserLocale());
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = isRtlLocale(locale) ? "rtl" : "ltr";
  }, [locale]);

  const content = landingPageContent[locale];

  const handleLocaleChange = (nextLocale: SupportedLocale) => {
    setLocale(nextLocale);
    if (hydrated) {
      writeStoredLocale(nextLocale);
    }
  };

  return (
    <main lang={locale} dir={isRtlLocale(locale) ? "rtl" : "ltr"} className="overflow-x-hidden">
      <section className="relative border-b border-white/10">
        <div className="absolute inset-0 bg-grid bg-[size:34px_34px] opacity-20" />
        <div className="container-shell relative py-7 sm:py-8 lg:py-10">
          <header className="glass-panel flex flex-wrap items-center justify-between gap-4 px-5 py-4 sm:px-6">
            <Logo className="max-w-full" />
            <div className="flex flex-wrap items-center gap-3">
              <nav className="flex items-center gap-1 text-sm">
                {content.nav.links.map((item) => (
                  <a
                    key={item.href}
                    href={item.href}
                    className="rounded-full px-4 py-2 text-slate-300 transition hover:bg-white/10 hover:text-white"
                  >
                    {item.label}
                  </a>
                ))}
              </nav>
              <LocaleSelector locale={locale} onChange={handleLocaleChange} />
              <a
                href="#download"
                className="inline-flex items-center gap-2 rounded-full bg-pokered px-5 py-3 font-bold text-white transition hover:bg-red-600"
              >
                {content.nav.primaryCta}
                <ArrowRight className="h-4 w-4" />
              </a>
            </div>
          </header>

          <div className="grid items-center gap-12 py-14 lg:grid-cols-[1.05fr_0.95fr] lg:py-20">
            <motion.div {...fadeUp}>
              <SectionBadge>{content.hero.eyebrow}</SectionBadge>
              <h1 className="mt-6 max-w-3xl font-[family-name:var(--font-montserrat)] text-5xl font-black leading-none text-white sm:text-6xl lg:text-7xl">
                {content.hero.title.split(content.hero.titleAccent).map((part, index, parts) => (
                  <span key={`${part}-${index}`}>
                    {part}
                    {index < parts.length - 1 ? (
                      <span className="text-pokeyellow">{content.hero.titleAccent}</span>
                    ) : null}
                  </span>
                ))}
              </h1>
              <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-300 sm:text-xl">
                {content.hero.copy}
              </p>
              <div className="mt-8 flex flex-col gap-4 sm:flex-row">
                <a
                  href="#download"
                  className="inline-flex items-center justify-center gap-2 rounded-full bg-pokered px-7 py-4 font-bold text-white transition hover:bg-red-600"
                >
                  {content.hero.primaryCta}
                  <Download className="h-5 w-5" />
                </a>
                <a
                  href="#how-it-works"
                  className="inline-flex items-center justify-center rounded-full border border-white/20 bg-white/5 px-7 py-4 font-bold text-white transition hover:bg-white/10"
                >
                  {content.hero.secondaryCta}
                </a>
              </div>
              <div className="mt-10 grid gap-4 text-sm text-slate-300 sm:grid-cols-3">
                {content.hero.stats.map((stat) => (
                  <div key={stat.label} className="glass-panel p-4">
                    <p className="text-3xl font-black text-white">{stat.value}</p>
                    <p className="mt-2">{stat.label}</p>
                  </div>
                ))}
              </div>
            </motion.div>

            <motion.div {...fadeUp} transition={{ duration: 0.65, delay: 0.1 }}>
              <div className="glass-panel p-5 sm:p-6">
                <div className="mb-3 flex items-center gap-2 border-b border-white/10 pb-3 text-xs uppercase tracking-[0.28em] text-slate-400">
                  <span className="h-2.5 w-2.5 rounded-full bg-pokered" />
                  Hero Demo
                </div>
                <video
                  className="aspect-video w-full rounded-2xl border border-white/10 bg-black/40 object-cover"
                  autoPlay
                  muted
                  loop
                  playsInline
                  controls
                  poster="/hero-demo-poster.svg"
                >
                  <source src="/hero-demo.mp4" type="video/mp4" />
                </video>
                <p className="mt-4 text-sm leading-6 text-slate-300">
                  The hero is wired to `/hero-demo.mp4` and `/hero-demo-poster.svg`, so the media can be swapped in later without changing the page shell.
                </p>
              </div>
            </motion.div>
          </div>
        </div>
      </section>

      <section className="container-shell py-14 sm:py-16">
        <motion.div
          {...fadeUp}
          className="grid gap-4 rounded-[2rem] border border-white/10 bg-gradient-to-r from-white/10 via-white/5 to-transparent p-6 sm:grid-cols-2 lg:grid-cols-4"
        >
          {content.proofStrip.map((item) => (
            <div
              key={item}
              className="rounded-3xl border border-white/10 bg-black/20 px-5 py-4 text-sm text-slate-200"
            >
              {item}
            </div>
          ))}
        </motion.div>
      </section>

      <section className="container-shell py-16">
        <SectionHeader
          eyebrow={content.valueSection.eyebrow}
          title={content.valueSection.title}
          copy={content.valueSection.copy}
        />
        <div className="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {content.valueSection.points.map((point, index) => (
            <motion.article
              key={point.title}
              {...fadeUp}
              transition={{ duration: 0.5, delay: index * 0.04 }}
              className="glass-panel p-6"
            >
              <div className="inline-flex rounded-2xl bg-pokered/15 p-3 text-pokeyellow">
                <FeatureGlyph icon={point.icon} />
              </div>
              <p className="mt-5 text-xs font-bold uppercase tracking-[0.24em] text-pokeyellow">
                {point.eyebrow}
              </p>
              <h3 className="mt-3 text-2xl font-black text-white">{point.title}</h3>
              <p className="mt-3 leading-7 text-slate-300">{point.body}</p>
            </motion.article>
          ))}
        </div>
      </section>

      <section id="features" className="container-shell py-16">
        <SectionHeader
          eyebrow={content.featureShowcase.eyebrow}
          title={content.featureShowcase.title}
          copy={content.featureShowcase.copy}
        />
        <div className="mt-10 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {content.featureShowcase.features.map((feature, index) => (
            <motion.article
              key={feature.title}
              {...fadeUp}
              transition={{ duration: 0.5, delay: index * 0.04 }}
              className="glass-panel p-6"
            >
              <div className="inline-flex rounded-2xl bg-pokered/15 p-3 text-pokeyellow">
                <FeatureGlyph icon={feature.icon} />
              </div>
              <p className="mt-5 text-xs font-bold uppercase tracking-[0.24em] text-pokeyellow">
                {feature.eyebrow}
              </p>
              <h3 className="mt-3 text-2xl font-black text-white">{feature.title}</h3>
              <p className="mt-3 leading-7 text-slate-300">{feature.body}</p>
            </motion.article>
          ))}
        </div>
      </section>

      <section id="how-it-works" className="container-shell py-16">
        <SectionHeader
          eyebrow={content.howItWorks.eyebrow}
          title={content.howItWorks.title}
          copy={content.howItWorks.copy}
        />
        <div className="mt-10 grid gap-4 lg:grid-cols-2">
          {content.howItWorks.steps.map((step, index) => (
            <motion.div
              key={step.title}
              {...fadeUp}
              transition={{ duration: 0.5, delay: index * 0.05 }}
              className="glass-panel flex gap-4 p-5"
            >
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-pokered font-black text-white">
                0{index + 1}
              </div>
              <div>
                <p className="text-lg font-bold text-white">{step.title}</p>
                <p className="mt-2 leading-7 text-slate-300">{step.body}</p>
              </div>
            </motion.div>
          ))}
        </div>
      </section>

      <section id="use-cases" className="container-shell py-16">
        <SectionHeader
          eyebrow={content.useCases.eyebrow}
          title={content.useCases.title}
          copy={content.useCases.copy}
        />
        <div className="mt-10 grid gap-5 lg:grid-cols-3">
          {content.useCases.items.map((item, index) => (
            <motion.article
              key={item.title}
              {...fadeUp}
              transition={{ duration: 0.5, delay: index * 0.05 }}
              className="glass-panel p-6"
            >
              <p className="text-sm font-bold uppercase tracking-[0.24em] text-pokeyellow">
                0{index + 1}
              </p>
              <h3 className="mt-4 text-2xl font-black text-white">{item.title}</h3>
              <p className="mt-3 leading-7 text-slate-300">{item.body}</p>
            </motion.article>
          ))}
        </div>
      </section>

      <section id="download" className="container-shell py-16">
        <motion.div
          {...fadeUp}
          className="relative overflow-hidden rounded-[2rem] border border-pokered/30 bg-gradient-to-br from-pokered via-red-700 to-zinc-950 p-8 shadow-soft lg:p-12"
        >
          <div className="absolute right-0 top-0 h-40 w-40 rounded-full bg-pokeyellow/25 blur-3xl" />
          <div className="relative grid gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
            <div>
              <SectionBadge>{content.finalCta.eyebrow}</SectionBadge>
              <h2 className="mt-5 font-[family-name:var(--font-montserrat)] text-4xl font-black text-white sm:text-5xl">
                {content.finalCta.title}
              </h2>
              <p className="mt-5 max-w-2xl text-lg leading-8 text-red-50/90">
                {content.finalCta.copy}
              </p>
              <div className="mt-7 flex flex-wrap gap-4">
                <a
                  href="#"
                  className="inline-flex items-center gap-2 rounded-full bg-white px-7 py-4 font-bold text-pokered transition hover:bg-slate-100"
                >
                  {content.finalCta.primaryCta}
                  <Download className="h-5 w-5" />
                </a>
                <a
                  href="#how-it-works"
                  className="inline-flex items-center rounded-full border border-white/20 px-7 py-4 font-bold text-white transition hover:bg-white/10"
                >
                  {content.finalCta.secondaryCta}
                </a>
              </div>
            </div>

            <div className="rounded-[1.75rem] border border-white/15 bg-black/20 p-6">
              <Logo compact showWordmark={false} />
              <p className="mt-4 text-sm leading-7 text-red-50/90">{content.footer.brandCopy}</p>
              <div className="mt-6 flex items-center gap-3 text-sm text-red-50/80">
                <TimerReset className="h-4 w-4 text-pokeyellow" />
                {content.footer.legalCopy}
              </div>
            </div>
          </div>
        </motion.div>
      </section>

      <footer className="container-shell pb-10 pt-3">
        <div className="glass-panel flex flex-col gap-5 px-6 py-7 sm:flex-row sm:items-center sm:justify-between">
          <Logo compact />
          <p className="text-sm text-slate-400">{content.footer.legalCopy}</p>
        </div>
      </footer>
    </main>
  );
}
