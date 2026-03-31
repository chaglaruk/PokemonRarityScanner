import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import './globals.css'

const inter = Inter({ subsets: ['latin'] })

export const metadata: Metadata = {
  title: 'PokeRarityScanner - Scan Pokemon Rarity Instantly',
  description: 'The fastest way to discover your rarest Pokemon. Real-time OCR analysis, shiny detection, and rarity scoring for Pokemon GO.',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className={`${inter.className} bg-white`}>
        {children}
      </body>
    </html>
  )
}
