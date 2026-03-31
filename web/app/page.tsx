import LandingPage from "@/components/landing-page";

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "PokeRarityScanner",
  applicationCategory: "GameApplication",
  operatingSystem: "Android",
  offers: {
    "@type": "Offer",
    price: "0",
    priceCurrency: "USD",
  },
  description:
    "Android app that scans Pokemon GO in real time, reads OCR data and calculates a rarity score for collectors, traders and battlers.",
  featureList: [
    "Real-time OCR scanning",
    "Shiny, shadow and lucky detection",
    "Radar overlay widget",
    "Rarity score output",
  ],
};

export default function Home() {
  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      <LandingPage />
    </>
  );
}
