'use client'

import { motion } from 'framer-motion'

export default function HowItWorks() {
  const steps = [
    {
      number: '1',
      title: 'Start App',
      description: 'Launch PokeRarityScanner and grant screen capture permission',
    },
    {
      number: '2',
      title: 'Open Pokemon',
      description: 'Navigate to any Pokemon card in Pokemon GO',
    },
    {
      number: '3',
      title: 'Tap Pokéball',
      description: 'Click the floating red Pokéball overlay to scan',
    },
    {
      number: '4',
      title: 'Get Results',
      description: 'Receive instant rarity analysis with full breakdowns',
    },
    {
      number: '5',
      title: 'See Score',
      description: 'View your Pokemon rarity ranking 1-100',
    },
  ]

  return (
    <section className="section-container bg-pokemon-gray" id="how-it-works">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        viewport={{ once: true }}
        className="text-center mb-16"
      >
        <h2 className="text-4xl md:text-5xl font-heading font-bold mb-4">5-Step Process</h2>
        <p className="text-xl text-gray-600 max-w-2xl mx-auto">
          From screenshot to rarity score in seconds
        </p>
      </motion.div>

      <div className="relative">
        {/* Timeline Line */}
        <div className="hidden md:block absolute top-20 left-0 right-0 h-1 bg-gradient-to-r from-transparent via-pokemon-red to-transparent"></div>

        {/* Steps */}
        <div className="grid md:grid-cols-5 gap-6 md:gap-4">
          {steps.map((step, index) => (
            <motion.div
              key={index}
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: index * 0.1 }}
              viewport={{ once: true }}
              className="relative"
            >
              {/* Circle */}
              <div className="flex justify-center mb-6">
                <motion.div
                  whileHover={{ scale: 1.2 }}
                  className="w-16 h-16 rounded-full bg-pokemon-red text-white flex items-center justify-center font-heading font-bold text-2xl shadow-lg"
                >
                  {step.number}
                </motion.div>
              </div>

              {/* Content */}
              <div className="bg-white rounded-lg p-6 text-center">
                <h3 className="font-heading font-bold text-lg mb-2 text-pokemon-darkText">{step.title}</h3>
                <p className="text-sm text-gray-600">{step.description}</p>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}
