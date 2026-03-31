'use client'

import { motion, AnimatePresence } from 'framer-motion'
import { useState } from 'react'

export default function FAQ() {
  const [openIndex, setOpenIndex] = useState<number | null>(0)

  const faqs = [
    {
      question: 'What Pokemon does the app support?',
      answer: 'PokeRarityScanner supports all 1000+ Pokemon from Pokemon GO, including all generations and regional variants.',
    },
    {
      question: 'Does it work offline?',
      answer: 'The app requires initial setup with internet, but can perform scans offline. Database updates refresh when connected.',
    },
    {
      question: 'Is my data private?',
      answer: 'Yes! Your scan history remains on your device. We never upload personal data to servers. Privacy is our priority.',
    },
    {
      question: 'Which phones are supported?',
      answer: 'Android 13+ (API 26+). Works on most modern Android phones. Tablet support coming soon.',
    },
    {
      question: 'How is the rarity score calculated?',
      answer: 'Our algorithm considers: IV stats, CP level, type advantages, current availability, Pokemon generation, special variants (Shiny/Shadow/Lucky), and community demand.',
    },
    {
      question: 'Can I export my scans?',
      answer: 'Yes! Export your collection as CSV, JSON, or shareable links. Premium features include cloud backup.',
    },
    {
      question: 'How often is data updated?',
      answer: 'Game Master data updates weekly. Rarity calculations adjust monthly based on new releases and meta changes.',
    },
    {
      question: 'Is there a desktop version?',
      answer: 'Web dashboard is in beta! Desktop app coming Q4 2026.',
    },
    {
      question: 'What\'s the subscription model?',
      answer: 'Free forever with basic features. Premium ($2.99/month) adds unlimited cloud storage, export, and advanced analytics.',
    },
    {
      question: 'How can I report bugs?',
      answer: 'Email us at support@pokerarityscanner.com or use in-app feedback. We fix critical issues within 24 hours.',
    },
  ]

  return (
    <section className="section-container" id="faq">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        viewport={{ once: true }}
        className="text-center mb-16"
      >
        <h2 className="text-4xl md:text-5xl font-heading font-bold mb-4">Frequently Asked Questions</h2>
        <p className="text-xl text-gray-600">Everything you need to know</p>
      </motion.div>

      <div className="max-w-3xl mx-auto space-y-4">
        {faqs.map((faq, index) => (
          <motion.div
            key={index}
            initial={{ opacity: 0, y: 10 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: index * 0.05 }}
            viewport={{ once: true }}
            className="border border-gray-200 rounded-lg overflow-hidden hover:border-pokemon-red transition-colors"
          >
            <button
              onClick={() => setOpenIndex(openIndex === index ? null : index)}
              className="w-full px-6 py-4 bg-white hover:bg-pokemon-gray flex justify-between items-center transition-colors"
            >
              <span className="font-heading font-bold text-left text-pokemon-darkText">{faq.question}</span>
              <motion.span
                animate={{ rotate: openIndex === index ? 180 : 0 }}
                transition={{ duration: 0.3 }}
                className="text-pokemon-red text-2xl flex-shrink-0 ml-4"
              >
                ▼
              </motion.span>
            </button>

            <AnimatePresence>
              {openIndex === index && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  transition={{ duration: 0.3 }}
                  className="border-t border-gray-200 bg-pokemon-gray"
                >
                  <p className="px-6 py-4 text-gray-700 leading-relaxed">{faq.answer}</p>
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        ))}
      </div>
    </section>
  )
}
