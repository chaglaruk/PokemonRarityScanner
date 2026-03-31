'use client'

import Link from 'next/link'
import { useState } from 'react'

export default function Header() {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [language, setLanguage] = useState('EN')

  const navLinks = [
    { label: 'Features', href: '#features' },
    { label: 'How It Works', href: '#how-it-works' },
    { label: 'FAQ', href: '#faq' },
    { label: 'Contact', href: '#contact' },
  ]

  return (
    <header className="fixed top-0 w-full bg-white z-50 transition-all duration-300 border-b border-gray-100">
      <div className="max-w-7xl mx-auto px-6 md:px-12 py-4 flex justify-between items-center">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2">
          <div className="w-10 h-10 rounded-full bg-pokemon-red flex items-center justify-center text-white font-bold text-lg">
            ◎
          </div>
          <span className="font-heading font-bold text-xl hidden sm:inline">PokeRarityScanner</span>
        </Link>

        {/* Desktop Navigation */}
        <nav className="hidden md:flex items-center gap-8">
          {navLinks.map((link) => (
            <Link
              key={link.label}
              href={link.href}
              className="text-sm font-medium text-gray-700 hover:text-pokemon-red transition-colors"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        {/* Right Section */}
        <div className="flex items-center gap-4">
          <div className="hidden sm:flex items-center gap-2 text-sm font-medium">
            <button
              onClick={() => setLanguage('EN')}
              className={`px-2 py-1 ${language === 'EN' ? 'text-pokemon-red' : 'text-gray-500'}`}
            >
              EN
            </button>
            <span className="text-gray-300">|</span>
            <button
              onClick={() => setLanguage('TR')}
              className={`px-2 py-1 ${language === 'TR' ? 'text-pokemon-red' : 'text-gray-500'}`}
            >
              TR
            </button>
          </div>
          <button className="btn-primary hidden sm:block">Download</button>

          {/* Mobile Menu Button */}
          <button
            onClick={() => setIsMenuOpen(!isMenuOpen)}
            className="md:hidden w-8 h-8 flex flex-col justify-center items-center gap-1.5"
          >
            <span className="block w-6 h-0.5 bg-pokemon-red rounded-full"></span>
            <span className="block w-6 h-0.5 bg-pokemon-red rounded-full"></span>
            <span className="block w-6 h-0.5 bg-pokemon-red rounded-full"></span>
          </button>
        </div>
      </div>

      {/* Mobile Menu */}
      {isMenuOpen && (
        <div className="md:hidden bg-white border-t border-gray-100 p-4">
          <nav className="flex flex-col gap-4">
            {navLinks.map((link) => (
              <Link
                key={link.label}
                href={link.href}
                className="text-sm font-medium text-gray-700 hover:text-pokemon-red transition-colors"
                onClick={() => setIsMenuOpen(false)}
              >
                {link.label}
              </Link>
            ))}
            <button className="btn-primary w-full">Download</button>
          </nav>
        </div>
      )}
    </header>
  )
}
