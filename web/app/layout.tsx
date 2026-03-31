import type { Metadata } from "next";
import { Inter, Montserrat } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
});

const montserrat = Montserrat({
  subsets: ["latin"],
  variable: "--font-montserrat",
  weight: ["700", "800", "900"],
});

export const metadata: Metadata = {
  metadataBase: new URL("https://pokerarityscanner.app"),
  title: {
    default: "PokeRarityScanner | Pokemon GO rarity scanner",
    template: "%s | PokeRarityScanner",
  },
  description:
    "PokeRarityScanner is an Android-first Pokemon GO scanner that uses OCR and visual analysis to surface a clear rarity score.",
  keywords: [
    "Pokemon GO helper",
    "Pokemon GO rarity scanner",
    "Pokemon GO OCR",
    "Android Pokemon scanner",
    "PokeRarityScanner",
  ],
  openGraph: {
    title: "PokeRarityScanner",
    description: "Scan Pokemon GO in seconds and turn visual analysis into a fast rarity signal.",
    type: "website",
    url: "https://pokerarityscanner.app",
    siteName: "PokeRarityScanner",
    locale: "en_US",
  },
  twitter: {
    card: "summary_large_image",
    title: "PokeRarityScanner",
    description: "Pokemon GO OCR and visual analysis for a clear rarity score.",
  },
  alternates: {
    canonical: "/",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} ${montserrat.variable}`}>
      <body className="font-sans">{children}</body>
    </html>
  );
}
