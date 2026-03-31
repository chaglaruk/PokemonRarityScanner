type LogoProps = {
  compact?: boolean;
  showWordmark?: boolean;
  className?: string;
};

function RadarEmblem({ compact = false }: { compact?: boolean }) {
  const size = compact ? 40 : 48;

  return (
    <svg
      viewBox="0 0 64 64"
      width={size}
      height={size}
      fill="none"
      role="img"
      aria-hidden="true"
      className="shrink-0"
    >
      <defs>
        <linearGradient id="logo-radar-glow" x1="18" y1="12" x2="50" y2="54" gradientUnits="userSpaceOnUse">
          <stop stopColor="#FFD47A" />
          <stop offset="0.55" stopColor="#FF9F4A" />
          <stop offset="1" stopColor="#E85C29" />
        </linearGradient>
      </defs>
      <circle cx="32" cy="32" r="31" fill="#0C1016" stroke="rgba(255,255,255,0.08)" />
      <circle cx="32" cy="32" r="19" stroke="rgba(224,161,86,0.38)" strokeWidth="2.5" />
      <circle cx="32" cy="32" r="11" stroke="rgba(224,161,86,0.58)" strokeWidth="2.5" />
      <circle cx="32" cy="32" r="5.5" fill="#FFF1D8" />
      <circle cx="32" cy="32" r="2.4" fill="#11161D" />
      <path d="M32 32L52 22" stroke="url(#logo-radar-glow)" strokeWidth="3.5" strokeLinecap="round" />
      <path d="M32 32L45 46" stroke="rgba(255,255,255,0.15)" strokeWidth="2" strokeLinecap="round" />
      <path d="M32 32L24 18" stroke="rgba(255,255,255,0.12)" strokeWidth="2" strokeLinecap="round" />
      <circle cx="52" cy="22" r="3.6" fill="#FFD27A" />
    </svg>
  );
}

export default function Logo({ compact = false, showWordmark = true, className }: LogoProps) {
  return (
    <div className={className ? `inline-flex items-center gap-3 ${className}` : "inline-flex items-center gap-3"}>
      <RadarEmblem compact={compact} />
      {showWordmark ? (
        <div className="leading-tight">
          <p className="text-[0.72rem] font-black tracking-[0.38em] text-white sm:text-[0.82rem]">
            POKERARITYSCANNER
          </p>
          <p className="mt-1 text-[0.72rem] text-slate-400">
            Radar scan for Pokemon GO rarity signals.
          </p>
        </div>
      ) : null}
    </div>
  );
}
