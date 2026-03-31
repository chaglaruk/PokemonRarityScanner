import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        pokered: "#E3350D",
        pokeyellow: "#FFDE00",
        pokeink: "#0B0D12",
        pokesilver: "#EEF2FF",
      },
      boxShadow: {
        soft: "0 20px 80px rgba(227,53,13,0.18)",
      },
      backgroundImage: {
        grid: "radial-gradient(circle at 1px 1px, rgba(255,255,255,0.08) 1px, transparent 0)",
      },
    },
  },
  plugins: [],
};

export default config;
