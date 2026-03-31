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

export const LOCALE_STORAGE_KEY = "prs-locale";

function resolveSupportedLocale(input?: string | null): SupportedLocale | null {
  if (!input) {
    return null;
  }

  const normalized = input.trim().toLowerCase().replace(/_/g, "-");

  return (
    supportedLocales.find(
      (locale) => normalized === locale || normalized.startsWith(`${locale}-`),
    ) ?? null
  );
}

export function matchSupportedLocale(input?: string | null): SupportedLocale {
  return resolveSupportedLocale(input) ?? defaultLocale;
}

export function detectBrowserLocale(): SupportedLocale {
  if (typeof navigator === "undefined") {
    return defaultLocale;
  }

  const candidates = [...navigator.languages, navigator.language].filter(Boolean);

  for (const candidate of candidates) {
    const locale = resolveSupportedLocale(candidate);
    if (locale) {
      return locale;
    }
  }

  return defaultLocale;
}

export function isRtlLocale(locale: SupportedLocale): boolean {
  return locale === "ar";
}

export function readStoredLocale(): SupportedLocale | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const storedLocale = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    return storedLocale ? matchSupportedLocale(storedLocale) : null;
  } catch {
    return null;
  }
}

export function writeStoredLocale(locale: SupportedLocale): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // Ignore storage failures and keep the selector functional.
  }
}
