'use client'

import { motion } from 'framer-motion'

export default function ValueProp() {
  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.2,
      },
    },
  }

  const itemVariants = {
    hidden: { opacity: 0, x: -30 },
    visible: {
      opacity: 1,
      x: 0,
      transition: { duration: 0.8 },
    },
  }

  const features = [
    { icon: '⚡', text: 'Instant rarity scoring' },
    { icon: '🎯', text: 'CP/HP detection' },
    { icon: '✨', text: 'Shiny recognition' },
    { icon: '📊', text: 'Advanced analytics' },
  ]

  return (
    <section className="section-container bg-pokemon-gray" id="features">
      <div className="grid md:grid-cols-2 gap-12 items-center">
        {/* Left: Image/Animation */}
        <motion.div
          initial={{ opacity: 0, x: -50 }}
          whileInView={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.8 }}
          viewport={{ once: true }}
          className="relative h-96 bg-gradient-to-br from-pokemon-red to-red-500 rounded-2xl overflow-hidden"
        >
          <div className="flex items-center justify-center h-full text-white text-center p-8">
            <div>
              <div className="text-6xl mb-4">📱</div>
              <p className="text-lg">App Scanning Demo</p>
            </div>
          </div>
        </motion.div>

        {/* Right: Features List */}
        <motion.div
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
        >
          <motion.h2 variants={itemVariants} className="text-4xl font-heading font-bold mb-6 text-pokemon-darkText">
            Unify your Pokemon knowledge
          </motion.h2>

          <motion.p variants={itemVariants} className="text-lg text-gray-700 mb-8">
            PokeRarityScanner combines real-time OCR scanning, visual analysis, and advanced algorithms to give you instant insights into your Pokemon collection.
          </motion.p>

          <motion.div variants={containerVariants} className="space-y-4">
            {features.map((feature, index) => (
              <motion.div
                key={index}
                variants={itemVariants}
                className="flex items-center gap-4 p-4 rounded-lg bg-white hover:shadow-md transition-shadow"
              >
                <span className="text-3xl">{feature.icon}</span>
                <span className="font-medium text-gray-800">{feature.text}</span>
              </motion.div>
            ))}
          </motion.div>
        </motion.div>
      </div>
    </section>
  )
}
