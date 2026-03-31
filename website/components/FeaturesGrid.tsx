'use client'

import { motion } from 'framer-motion'

export default function FeaturesGrid() {
  const features = [
    {
      icon: '⚫',
      title: 'Scan',
      description: 'Point, tap, and capture any Pokemon card instantly with our overlay widget',
    },
    {
      icon: '⚙️',
      title: 'Analyze',
      description: 'AI-powered OCR reads all card details including CP, HP, date, and species',
    },
    {
      icon: '⭐',
      title: 'Score',
      description: 'Get instant rarity analysis from 1-100 with detailed breakdowns',
    },
    {
      icon: '📋',
      title: 'Organize',
      description: 'Build and manage your perfect collection with scan history',
    },
  ]

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.2,
      },
    },
  }

  const cardVariants = {
    hidden: { opacity: 0, y: 30 },
    visible: (i: number) => ({
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.6,
        delay: i * 0.1,
      },
    }),
  }

  return (
    <section className="section-container">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        viewport={{ once: true }}
        className="text-center mb-16"
      >
        <h2 className="text-4xl md:text-5xl font-heading font-bold mb-4">How It Works</h2>
        <p className="text-xl text-gray-600 max-w-2xl mx-auto">
          Four simple steps to master your Pokemon collection
        </p>
      </motion.div>

      <motion.div
        variants={containerVariants}
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true }}
        className="grid md:grid-cols-2 lg:grid-cols-4 gap-6"
      >
        {features.map((feature, index) => (
          <motion.div
            key={index}
            custom={index}
            variants={cardVariants}
            className="group bg-white p-8 rounded-xl border border-gray-100 hover:shadow-2xl transition-all duration-300 cursor-pointer"
          >
            <div className="text-5xl mb-4 group-hover:scale-110 transition-transform">{feature.icon}</div>
            <h3 className="text-2xl font-heading font-bold mb-3 text-pokemon-darkText">{feature.title}</h3>
            <p className="text-gray-600">{feature.description}</p>
            <div className="mt-6 h-1 w-0 bg-pokemon-red group-hover:w-full transition-all duration-300"></div>
          </motion.div>
        ))}
      </motion.div>
    </section>
  )
}
