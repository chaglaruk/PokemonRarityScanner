import { defaultLocale, localeLabels, supportedLocales, type SupportedLocale } from "@/lib/locale";

export { defaultLocale, localeLabels, supportedLocales };
export type { SupportedLocale };

type NavItem = {
  href: string;
  label: string;
};

type Stat = {
  value: string;
  label: string;
};

type Feature = {
  eyebrow: string;
  title: string;
  body: string;
  icon: "scan" | "analyze" | "score";
};

type Step = {
  title: string;
  body: string;
};

type UseCase = {
  title: string;
  body: string;
};

type LandingPageContent = {
  nav: {
    links: NavItem[];
    primaryCta: string;
  };
  hero: {
    eyebrow: string;
    title: string;
    titleAccent: string;
    copy: string;
    primaryCta: string;
    secondaryCta: string;
    stats: Stat[];
  };
  proofStrip: string[];
  valueSection: {
    eyebrow: string;
    title: string;
    copy: string;
    points: Feature[];
  };
  featureShowcase: {
    eyebrow: string;
    title: string;
    copy: string;
    features: Feature[];
  };
  howItWorks: {
    eyebrow: string;
    title: string;
    copy: string;
    steps: Step[];
  };
  useCases: {
    eyebrow: string;
    title: string;
    copy: string;
    items: UseCase[];
  };
  finalCta: {
    eyebrow: string;
    title: string;
    copy: string;
    primaryCta: string;
    secondaryCta: string;
  };
  footer: {
    brandCopy: string;
    legalCopy: string;
  };
};

const content = {
  en: {
    nav: {
      links: [
        { href: "#features", label: "Features" },
        { href: "#how-it-works", label: "How it works" },
        { href: "#use-cases", label: "Use cases" },
      ],
      primaryCta: "Download App",
    },
    hero: {
      eyebrow: "English-first localized landing page",
      title: "Scan Pokemon GO and score rarity in seconds.",
      titleAccent: "score rarity",
      copy:
        "PokeRarityScanner gives collectors, traders, and battlers a fast overlay flow for reading Pokemon, detecting special traits, and turning the result into a clear rarity signal.",
      primaryCta: "Download App",
      secondaryCta: "See How It Works",
      stats: [
        { value: "Fast", label: "scan flow" },
        { value: "Instant", label: "rarity signal" },
        { value: "Overlay", label: "first workflow" },
      ],
    },
    proofStrip: [
      "Real-time Pokemon GO scan",
      "Instant rarity score",
      "Collector, trade, and battle support",
      "English-first with locale switching",
    ],
    valueSection: {
      eyebrow: "What you get",
      title: "A cleaner signal from every scan.",
      copy:
        "The page focuses on value, not pain points: clearer readout, quicker decisions, and a product flow that stays useful while you play.",
      points: [
        {
          icon: "scan",
          eyebrow: "Instant signal",
          title: "Turn a scan into a clear rarity readout.",
          body: "The interface brings together Pokemon details, visual cues, and scoring into one fast result.",
        },
        {
          icon: "analyze",
          eyebrow: "Overlay workflow",
          title: "Stay in Pokemon GO while the scan runs.",
          body: "A compact in-game flow keeps the scanner close to the action and avoids a context switch.",
        },
        {
          icon: "score",
          eyebrow: "Decision support",
          title: "Use the result for trade, collection, or battle calls.",
          body: "The output is built to help you decide what to keep, what to trade, and what to evaluate next.",
        },
      ],
    },
    featureShowcase: {
      eyebrow: "Feature showcase",
      title: "Three stages of a premium scan flow.",
      copy:
        "The product story is kept tight: detect, analyze, and score. Each block stays focused on what happens during the scan.",
      features: [
        {
          icon: "scan",
          eyebrow: "Scan",
          title: "Capture the Pokemon context quickly.",
          body: "A fast-first pass reads the Pokemon GO screen and keeps the interface anchored to the live overlay.",
        },
        {
          icon: "analyze",
          eyebrow: "Analyze",
          title: "Interpret species, traits, and visual markers.",
          body: "OCR and image analysis work together so the result is more dependable than a single signal.",
        },
        {
          icon: "score",
          eyebrow: "Score",
          title: "Turn the result into a rarity score.",
          body: "The final output is a compact signal that works for collectors, traders, and battlers.",
        },
      ],
    },
    howItWorks: {
      eyebrow: "How it works",
      title: "A short flow from tap to result.",
      copy:
        "The sequence stays simple so visitors can understand the product in a few seconds.",
      steps: [
        {
          title: "Open Pokemon GO and tap the overlay.",
          body: "The scanner stays close to the game instead of forcing a separate workflow.",
        },
        {
          title: "Capture the live Pokemon screen.",
          body: "The app reads the scene and prepares the signals needed for analysis.",
        },
        {
          title: "Analyze the species and special traits.",
          body: "Visual matching and OCR work together to build a reliable result.",
        },
        {
          title: "Review the rarity score and next action.",
          body: "The final state is designed for quick decisions and easy follow-up.",
        },
      ],
    },
    useCases: {
      eyebrow: "Use cases",
      title: "Useful across collection, trade, and battle decisions.",
      copy:
        "The same localized flow works for different play styles without changing the core story.",
      items: [
        {
          title: "Collection checks",
          body: "Spot the Pokemon worth keeping and move on quickly when the result is obvious.",
        },
        {
          title: "Trade prep",
          body: "Check rarity before a trade so the decision is easier to trust.",
        },
        {
          title: "Battle planning",
          body: "Keep stronger candidates visible when you are preparing a team or filtering options.",
        },
      ],
    },
    finalCta: {
      eyebrow: "Ready to scan?",
      title: "Download the app and use the rarity signal right away.",
      copy:
        "The page is built to push the app download as the primary action, with a direct path back to the explanation if needed.",
      primaryCta: "Download App",
      secondaryCta: "See How It Works",
    },
    footer: {
      brandCopy: "Independent Pokemon GO helper experience.",
      legalCopy: "© 2026 PokeRarityScanner. Android-first preview.",
    },
  },
  tr: {
    nav: {
      links: [
        { href: "#features", label: "Özellikler" },
        { href: "#how-it-works", label: "Nasıl çalışır" },
        { href: "#use-cases", label: "Kullanım alanları" },
      ],
      primaryCta: "Uygulamayı indir",
    },
    hero: {
      eyebrow: "İngilizce öncelikli yerelleştirilmiş açılış sayfası",
      title: "Pokemon GO'yu tara, nadirlik skorunu saniyeler içinde gör.",
      titleAccent: "nadirlik skorunu",
      copy:
        "PokeRarityScanner, koleksiyonculara, takas yapanlara ve battle oyuncularına Pokemon'ı okumak, özel sinyalleri algılamak ve sonucu net bir rarity sinyaline dönüştürmek için hızlı bir overlay akışı sunar.",
      primaryCta: "Uygulamayı indir",
      secondaryCta: "Nasıl çalıştığını gör",
      stats: [
        { value: "Hızlı", label: "tarama akışı" },
        { value: "Anlık", label: "rarity sinyali" },
        { value: "Overlay", label: "öncelikli kullanım" },
      ],
    },
    proofStrip: [
      "Gerçek zamanlı Pokemon GO taraması",
      "Anında rarity skoru",
      "Koleksiyon, takas ve battle desteği",
      "Yerel dil seçimiyle İngilizce öncelikli yapı",
    ],
    valueSection: {
      eyebrow: "Sana ne sağlar",
      title: "Her taramadan daha net bir sinyal al.",
      copy:
        "Sayfa, sorun anlatmak yerine değeri öne çıkarır: daha net okuma, daha hızlı karar ve oynarken işleyen bir ürün akışı.",
      points: [
        {
          icon: "scan",
          eyebrow: "Anlık sinyal",
          title: "Taramayı net bir rarity çıktısına dönüştür.",
          body: "Arayüz, Pokemon detaylarını, görsel ipuçlarını ve skoru tek hızlı sonuçta birleştirir.",
        },
        {
          icon: "analyze",
          eyebrow: "Overlay akışı",
          title: "Tarama çalışırken Pokemon GO içinde kal.",
          body: "Kompakt oyun içi akış, tarayıcıyı aksiyona yakın tutar ve bağlam değişimini azaltır.",
        },
        {
          icon: "score",
          eyebrow: "Karar desteği",
          title: "Sonucu takas, koleksiyon veya battle kararı için kullan.",
          body: "Çıktı, neyi tutacağını, neyi takas edeceğini ve bir sonraki Pokemon'ı nasıl değerlendireceğini anlamana yardım eder.",
        },
      ],
    },
    featureShowcase: {
      eyebrow: "Özellikler",
      title: "Premium tarama akışının üç aşaması.",
      copy:
        "Ürün hikayesi sıkı tutulur: algıla, analiz et, skorla. Her blok tarama sırasında olanlara odaklanır.",
      features: [
        {
          icon: "scan",
          eyebrow: "Tara",
          title: "Pokemon bağlamını hızlıca yakala.",
          body: "Hız odaklı ilk geçiş Pokemon GO ekranını okur ve arayüzü canlı overlay'e bağlı tutar.",
        },
        {
          icon: "analyze",
          eyebrow: "Analiz et",
          title: "Species, trait ve görsel işaretleri yorumla.",
          body: "OCR ve görüntü analizi birlikte çalışır, böylece sonuç tek sinyalden daha güvenilir olur.",
        },
        {
          icon: "score",
          eyebrow: "Skorla",
          title: "Sonucu bir rarity skoruna dönüştür.",
          body: "Son çıktı, koleksiyoncular, takasçılar ve battle oyuncuları için kompakt bir sinyaldir.",
        },
      ],
    },
    howItWorks: {
      eyebrow: "Nasıl çalışır",
      title: "Dokunuştan sonuca giden kısa akış.",
      copy:
        "Sıra sade tutulur ki ziyaretçi ürünü birkaç saniyede anlayabilsin.",
      steps: [
        {
          title: "Pokemon GO'yu aç ve overlay'e dokun.",
          body: "Tarayıcı, oyuna yakın kalır ve ayrı bir iş akışı zorlamaz.",
        },
        {
          title: "Canlı Pokemon ekranını yakala.",
          body: "Uygulama sahneyi okur ve analiz için gereken sinyalleri hazırlar.",
        },
        {
          title: "Species ve özel trait'leri analiz et.",
          body: "Görsel eşleme ve OCR, güvenilir bir sonuç oluşturmak için birlikte çalışır.",
        },
        {
          title: "Rarity skorunu ve sonraki adımı gör.",
          body: "Son durum, hızlı karar ve kolay takip için tasarlanır.",
        },
      ],
    },
    useCases: {
      eyebrow: "Kullanım alanları",
      title: "Koleksiyon, takas ve battle kararlarında işe yarar.",
      copy:
        "Aynı yerelleştirilmiş akış, ana hikayeyi değiştirmeden farklı oyun tarzlarında çalışır.",
      items: [
        {
          title: "Koleksiyon kontrolü",
          body: "Tutmaya değer Pokemon'ı hızlıca gör ve sonuç açıksa devam et.",
        },
        {
          title: "Takas hazırlığı",
          body: "Takas öncesi rarity kontrolü yaparak kararı daha güvenli hale getir.",
        },
        {
          title: "Battle planlama",
          body: "Takım hazırlarken veya seçenekleri filtrelerken güçlü adayları görünür tut.",
        },
      ],
    },
    finalCta: {
      eyebrow: "Taramaya hazır mısın?",
      title: "Uygulamayı indir ve rarity sinyalini hemen kullan.",
      copy:
        "Sayfa, uygulama indirmeyi birincil aksiyon olarak öne çıkarır ve gerektiğinde açıklamaya geri dönüş sağlar.",
      primaryCta: "Uygulamayı indir",
      secondaryCta: "Nasıl çalıştığını gör",
    },
    footer: {
      brandCopy: "Bağımsız Pokemon GO yardımcı deneyimi.",
      legalCopy: "© 2026 PokeRarityScanner. Android öncelikli önizleme.",
    },
  },
  de: {
    nav: {
      links: [
        { href: "#features", label: "Funktionen" },
        { href: "#how-it-works", label: "So funktioniert es" },
        { href: "#use-cases", label: "Einsatzbereiche" },
      ],
      primaryCta: "App herunterladen",
    },
    hero: {
      eyebrow: "Englisch zuerst lokalisiertes Landingpage-Erlebnis",
      title: "Scanne Pokemon GO und bewerte die Seltenheit in Sekunden.",
      titleAccent: "bewerte die Seltenheit",
      copy:
        "PokeRarityScanner gibt Sammlern, Tauschern und Kämpfern einen schnellen Overlay-Flow, um Pokemon zu lesen, besondere Merkmale zu erkennen und das Ergebnis in ein klares Seltenheitssignal zu verwandeln.",
      primaryCta: "App herunterladen",
      secondaryCta: "So funktioniert es",
      stats: [
        { value: "Schnell", label: "Scan-Flow" },
        { value: "Sofort", label: "Seltenheitssignal" },
        { value: "Overlay", label: "zuerst" },
      ],
    },
    proofStrip: [
      "Pokemon GO Scan in Echtzeit",
      "Seltenheitswert sofort sichtbar",
      "Unterstützung für Sammlung, Tausch und Kampf",
      "Englisch zuerst mit Sprachumschaltung",
    ],
    valueSection: {
      eyebrow: "Was du bekommst",
      title: "Ein klareres Signal aus jedem Scan.",
      copy:
        "Die Seite setzt auf Nutzen statt auf Schmerz: klarere Ausgabe, schnellere Entscheidungen und ein Flow, der beim Spielen nützlich bleibt.",
      points: [
        {
          icon: "scan",
          eyebrow: "Sofortsignal",
          title: "Mach aus einem Scan eine klare Seltenheitsanzeige.",
          body: "Die Oberfläche bündelt Pokemon-Details, visuelle Hinweise und Scoring in einem schnellen Ergebnis.",
        },
        {
          icon: "analyze",
          eyebrow: "Overlay-Flow",
          title: "Bleib in Pokemon GO, während der Scan läuft.",
          body: "Ein kompakter In-Game-Flow hält den Scanner nah am Geschehen und vermeidet Kontextwechsel.",
        },
        {
          icon: "score",
          eyebrow: "Entscheidungshilfe",
          title: "Nutze das Ergebnis für Tausch, Sammlung oder Kampf.",
          body: "Die Ausgabe hilft dir zu entscheiden, was du behältst, tauschst oder als Nächstes prüfst.",
        },
      ],
    },
    featureShowcase: {
      eyebrow: "Funktionsübersicht",
      title: "Drei Stufen eines Premium-Scan-Flows.",
      copy:
        "Die Produktgeschichte bleibt klar: erkennen, analysieren, bewerten. Jeder Block bleibt auf den Scan-Moment fokussiert.",
      features: [
        {
          icon: "scan",
          eyebrow: "Scannen",
          title: "Erfasse den Pokemon-Kontext schnell.",
          body: "Ein schneller erster Durchlauf liest den Pokemon GO Bildschirm und hält die Oberfläche am Live-Overlay.",
        },
        {
          icon: "analyze",
          eyebrow: "Analysieren",
          title: "Deute Species, Merkmale und visuelle Marker.",
          body: "OCR und Bildanalyse arbeiten zusammen, damit das Ergebnis verlässlicher wird als ein einzelnes Signal.",
        },
        {
          icon: "score",
          eyebrow: "Bewerten",
          title: "Wandle das Ergebnis in einen Seltenheitswert um.",
          body: "Das Endergebnis ist ein kompaktes Signal für Sammler, Tauschende und Kämpfende.",
        },
      ],
    },
    howItWorks: {
      eyebrow: "So funktioniert es",
      title: "Ein kurzer Ablauf vom Tippen bis zum Ergebnis.",
      copy:
        "Die Reihenfolge bleibt einfach, damit Besucher das Produkt in wenigen Sekunden verstehen.",
      steps: [
        {
          title: "Pokemon GO öffnen und das Overlay antippen.",
          body: "Der Scanner bleibt nah am Spiel und erzwingt keinen separaten Workflow.",
        },
        {
          title: "Den Live-Pokemon-Bildschirm erfassen.",
          body: "Die App liest die Szene und bereitet die Signale für die Analyse vor.",
        },
        {
          title: "Species und besondere Merkmale analysieren.",
          body: "Visuelles Matching und OCR erzeugen gemeinsam ein belastbares Ergebnis.",
        },
        {
          title: "Seltenheitswert und nächsten Schritt prüfen.",
          body: "Der Endzustand ist auf schnelle Entscheidungen und einfache Folgeaktionen ausgelegt.",
        },
      ],
    },
    useCases: {
      eyebrow: "Einsatzbereiche",
      title: "Hilfreich für Sammlung, Tausch und Kampfentscheidungen.",
      copy:
        "Der gleiche lokalisierte Flow funktioniert für verschiedene Spielstile, ohne die Kernbotschaft zu ändern.",
      items: [
        {
          title: "Sammlungscheck",
          body: "Erkenne schnell, welches Pokemon du behalten willst, und gehe weiter, wenn das Ergebnis klar ist.",
        },
        {
          title: "Tauschvorbereitung",
          body: "Prüfe die Seltenheit vor einem Tausch, damit die Entscheidung leichter vertrauenswürdig ist.",
        },
        {
          title: "Kampfplanung",
          body: "Halte stärkere Kandidaten sichtbar, während du ein Team vorbereitest oder Optionen filterst.",
        },
      ],
    },
    finalCta: {
      eyebrow: "Bereit zum Scannen?",
      title: "Lade die App herunter und nutze das Seltenheitssignal sofort.",
      copy:
        "Die Seite stellt den App-Download als Hauptaktion in den Vordergrund und bietet bei Bedarf einen direkten Rückweg zur Erklärung.",
      primaryCta: "App herunterladen",
      secondaryCta: "So funktioniert es",
    },
    footer: {
      brandCopy: "Unabhängige Pokemon GO-Hilfe.",
      legalCopy: "© 2026 PokeRarityScanner. Android-first Vorschau.",
    },
  },
  es: {
    nav: {
      links: [
        { href: "#features", label: "Funciones" },
        { href: "#how-it-works", label: "Cómo funciona" },
        { href: "#use-cases", label: "Casos de uso" },
      ],
      primaryCta: "Descargar app",
    },
    hero: {
      eyebrow: "Página de inicio localizada con prioridad en inglés",
      title: "Escanea Pokemon GO y evalúa rareza en segundos.",
      titleAccent: "evalúa rareza",
      copy:
        "PokeRarityScanner ofrece a coleccionistas, intercambiadores y combatientes un flujo rápido de overlay para leer Pokemon, detectar rasgos especiales y convertir el resultado en una señal clara de rareza.",
      primaryCta: "Descargar app",
      secondaryCta: "Ver cómo funciona",
      stats: [
        { value: "Rápido", label: "flujo de escaneo" },
        { value: "Instantáneo", label: "señal de rareza" },
        { value: "Overlay", label: "primero" },
      ],
    },
    proofStrip: [
      "Escaneo de Pokemon GO en tiempo real",
      "Puntuación de rareza inmediata",
      "Soporte para colección, intercambio y combate",
      "Inglés primero con cambio de idioma",
    ],
    valueSection: {
      eyebrow: "Qué obtienes",
      title: "Una señal más clara en cada escaneo.",
      copy:
        "La página se centra en el valor, no en los problemas: una lectura más clara, decisiones más rápidas y un flujo útil mientras juegas.",
      points: [
        {
          icon: "scan",
          eyebrow: "Señal instantánea",
          title: "Convierte un escaneo en una lectura clara de rareza.",
          body: "La interfaz reúne detalles de Pokemon, pistas visuales y puntuación en un resultado rápido.",
        },
        {
          icon: "analyze",
          eyebrow: "Flujo de overlay",
          title: "Quédate en Pokemon GO mientras corre el escaneo.",
          body: "Un flujo compacto dentro del juego mantiene el scanner cerca de la acción y evita cambiar de contexto.",
        },
        {
          icon: "score",
          eyebrow: "Ayuda para decidir",
          title: "Usa el resultado para intercambios, colección o combate.",
          body: "La salida te ayuda a decidir qué conservar, qué intercambiar y qué revisar después.",
        },
      ],
    },
    featureShowcase: {
      eyebrow: "Funciones",
      title: "Tres etapas de un flujo premium de escaneo.",
      copy:
        "La historia del producto se mantiene ajustada: detectar, analizar y puntuar. Cada bloque se enfoca en el momento del escaneo.",
      features: [
        {
          icon: "scan",
          eyebrow: "Escanear",
          title: "Captura rápido el contexto de Pokemon.",
          body: "Una primera pasada veloz lee la pantalla de Pokemon GO y mantiene la interfaz anclada al overlay en vivo.",
        },
        {
          icon: "analyze",
          eyebrow: "Analizar",
          title: "Interpreta species, rasgos y marcadores visuales.",
          body: "OCR y análisis de imagen trabajan juntos para que el resultado sea más fiable que una sola señal.",
        },
        {
          icon: "score",
          eyebrow: "Puntuar",
          title: "Convierte el resultado en una puntuación de rareza.",
          body: "La salida final es una señal compacta para coleccionistas, intercambiadores y combatientes.",
        },
      ],
    },
    howItWorks: {
      eyebrow: "Cómo funciona",
      title: "Un flujo corto desde el toque hasta el resultado.",
      copy:
        "La secuencia se mantiene simple para que el visitante entienda el producto en segundos.",
      steps: [
        {
          title: "Abre Pokemon GO y toca el overlay.",
          body: "El scanner se mantiene cerca del juego y no obliga a usar otro flujo.",
        },
        {
          title: "Captura la pantalla en vivo de Pokemon.",
          body: "La app lee la escena y prepara las señales necesarias para el análisis.",
        },
        {
          title: "Analiza species y rasgos especiales.",
          body: "El matching visual y el OCR se combinan para construir un resultado fiable.",
        },
        {
          title: "Revisa la rareza y la siguiente acción.",
          body: "El estado final está pensado para decisiones rápidas y seguimiento sencillo.",
        },
      ],
    },
    useCases: {
      eyebrow: "Casos de uso",
      title: "Útil para colección, intercambio y combate.",
      copy:
        "El mismo flujo localizado funciona para distintos estilos de juego sin cambiar la historia principal.",
      items: [
        {
          title: "Revisión de colección",
          body: "Detecta rápido qué Pokemon vale la pena conservar y sigue cuando el resultado es claro.",
        },
        {
          title: "Preparación de intercambio",
          body: "Comprueba la rareza antes de un intercambio para que la decisión sea más fácil de confiar.",
        },
        {
          title: "Planificación de combate",
          body: "Mantén visibles los candidatos fuertes mientras preparas un equipo o filtras opciones.",
        },
      ],
    },
    finalCta: {
      eyebrow: "¿Listo para escanear?",
      title: "Descarga la app y usa la señal de rareza de inmediato.",
      copy:
        "La página está diseñada para que la descarga de la app sea la acción principal, con un camino directo de vuelta a la explicación si hace falta.",
      primaryCta: "Descargar app",
      secondaryCta: "Ver cómo funciona",
    },
    footer: {
      brandCopy: "Experiencia independiente de ayuda para Pokemon GO.",
      legalCopy: "© 2026 PokeRarityScanner. Vista previa centrada en Android.",
    },
  },
  ar: {
    nav: {
      links: [
        { href: "#features", label: "الميزات" },
        { href: "#how-it-works", label: "كيف يعمل" },
        { href: "#use-cases", label: "حالات الاستخدام" },
      ],
      primaryCta: "تنزيل التطبيق",
    },
    hero: {
      eyebrow: "صفحة هبوط محلية مع أولوية للإنجليزية",
      title: "امسح Pokemon GO واحصل على نتيجة الندرة خلال ثوانٍ.",
      titleAccent: "نتيجة الندرة",
      copy:
        "يمنحك PokeRarityScanner تدفق overlay سريعًا لقراءة Pokemon، واكتشاف السمات الخاصة، وتحويل النتيجة إلى إشارة ندرة واضحة للاعبين والجامعين والمتبادلين.",
      primaryCta: "تنزيل التطبيق",
      secondaryCta: "شاهد كيف يعمل",
      stats: [
        { value: "سريع", label: "تدفق المسح" },
        { value: "فوري", label: "إشارة الندرة" },
        { value: "Overlay", label: "أولاً" },
      ],
    },
    proofStrip: [
      "مسح Pokemon GO في الوقت الفعلي",
      "درجة ندرة فورية",
      "دعم الجمع والتبادل والقتال",
      "الإنجليزية أولاً مع تبديل اللغة",
    ],
    valueSection: {
      eyebrow: "ما الذي تحصل عليه",
      title: "إشارة أوضح من كل عملية مسح.",
      copy:
        "تُركز الصفحة على القيمة لا على الألم: قراءة أوضح، قرارات أسرع، وتدفق مفيد أثناء اللعب.",
      points: [
        {
          icon: "scan",
          eyebrow: "إشارة فورية",
          title: "حوّل المسح إلى قراءة ندرة واضحة.",
          body: "تجمع الواجهة تفاصيل Pokemon والإشارات البصرية والتقييم في نتيجة سريعة واحدة.",
        },
        {
          icon: "analyze",
          eyebrow: "تدفق overlay",
          title: "ابقَ داخل Pokemon GO أثناء عمل المسح.",
          body: "يحافظ التدفق داخل اللعبة على قرب الأداة من الحدث ويقلل الحاجة لتبديل السياق.",
        },
        {
          icon: "score",
          eyebrow: "دعم القرار",
          title: "استخدم النتيجة للجمع أو التبادل أو القتال.",
          body: "تم تصميم المخرجات لمساعدتك على معرفة ما الذي تحتفظ به وما الذي تتبادله وما الذي تراجعه بعد ذلك.",
        },
      ],
    },
    featureShowcase: {
      eyebrow: "الميزات",
      title: "ثلاث مراحل لتدفق مسح مميز.",
      copy:
        "تبقى قصة المنتج مركزة: اكتشف، حلل، قيّم. يظل كل جزء مرتبطًا بلحظة المسح.",
      features: [
        {
          icon: "scan",
          eyebrow: "امسح",
          title: "التقط سياق Pokemon بسرعة.",
          body: "تقرأ المرحلة الأولى السريعة شاشة Pokemon GO وتبقي الواجهة مرتبطة بالـ overlay المباشر.",
        },
        {
          icon: "analyze",
          eyebrow: "حلل",
          title: "فسر species والسمات والمؤشرات البصرية.",
          body: "يعمل OCR وتحليل الصورة معًا ليكون الناتج أكثر موثوقية من إشارة واحدة.",
        },
        {
          icon: "score",
          eyebrow: "قيّم",
          title: "حوّل النتيجة إلى درجة ندرة.",
          body: "المخرجات النهائية إشارة مدمجة تناسب الجامعين والمتبادلين واللاعبين.",
        },
      ],
    },
    howItWorks: {
      eyebrow: "كيف يعمل",
      title: "تدفق قصير من اللمس إلى النتيجة.",
      copy:
        "يبقى التسلسل بسيطًا كي يفهم الزائر المنتج خلال ثوانٍ معدودة.",
      steps: [
        {
          title: "افتح Pokemon GO واضغط على overlay.",
          body: "تبقى الأداة قريبة من اللعبة ولا تفرض مسارًا منفصلًا.",
        },
        {
          title: "التقط شاشة Pokemon الحية.",
          body: "يقرأ التطبيق المشهد ويجهز الإشارات اللازمة للتحليل.",
        },
        {
          title: "حلل species والسمات الخاصة.",
          body: "يعمل المطابقة البصرية وOCR معًا لبناء نتيجة موثوقة.",
        },
        {
          title: "راجع درجة الندرة والخطوة التالية.",
          body: "تم تصميم الحالة النهائية لقرارات سريعة ومتابعة سهلة.",
        },
      ],
    },
    useCases: {
      eyebrow: "حالات الاستخدام",
      title: "مفيد للجمع والتبادل والقرارات القتالية.",
      copy:
        "يعمل التدفق نفسه بعدة لغات وأنماط لعب من دون تغيير القصة الأساسية.",
      items: [
        {
          title: "فحص المجموعة",
          body: "اعرف بسرعة أي Pokemon يستحق الاحتفاظ به وتابع عندما تكون النتيجة واضحة.",
        },
        {
          title: "التحضير للتبادل",
          body: "تحقق من الندرة قبل التبادل حتى يكون القرار أسهل في الثقة.",
        },
        {
          title: "تخطيط القتال",
          body: "أبقِ المرشحين الأقوى ظاهرين أثناء إعداد الفريق أو تصفية الخيارات.",
        },
      ],
    },
    finalCta: {
      eyebrow: "هل أنت جاهز للمسح؟",
      title: "نزّل التطبيق واستخدم إشارة الندرة مباشرة.",
      copy:
        "تم بناء الصفحة بحيث يكون تنزيل التطبيق هو الإجراء الرئيسي، مع طريق واضح للعودة إلى الشرح عند الحاجة.",
      primaryCta: "تنزيل التطبيق",
      secondaryCta: "شاهد كيف يعمل",
    },
    footer: {
      brandCopy: "تجربة مساعدة مستقلة لـ Pokemon GO.",
      legalCopy: "© 2026 PokeRarityScanner. نسخة أولية تركز على Android.",
    },
  },
} satisfies Record<SupportedLocale, LandingPageContent>;

export const landingPageContent = content;
