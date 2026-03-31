'use client'

import { motion } from 'framer-motion'

export default function Hero() {
  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.2,
        delayChildren: 0.3,
      },
    },
  }

  const itemVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: { duration: 0.8, ease: 'easeOut' },
    },
  }

  return (
    <section className="relative w-full h-screen bg-gradient-to-br from-pokemon-red via-red-500 to-red-600 overflow-hidden pt-20 flex items-center">
      {/* Animated Background Elements */}
      <div className="absolute inset-0 overflow-hidden">
        <motion.div
          className="absolute top-10 right-10 w-32 h-32 rounded-full border-4 border-white opacity-10"
          animate={{ y: [0, -20, 0], rotate: 360 }}
          transition={{ duration: 20, repeat: Infinity }}
        />
        <motion.div
          className="absolute bottom-20 left-10 w-40 h-40 rounded-full border-4 border-white opacity-10"
          animate={{ y: [0, 20, 0], rotate: -360 }}
          transition={{ duration: 20, repeat: Infinity }}
        />
      </div>

      <div className="section-container relative z-10 flex flex-col items-center text-center text-white">
        <motion.div
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          <motion.h1 variants={itemVariants} className="text-5xl md:text-6xl lg:text-7xl font-heading font-bold mb-6">
            Scan once.{' '}
            <span className="text-yellow-300">Know everything.</span>
          </motion.h1>

          <motion.p variants={itemVariants} className="text-xl md:text-2xl mb-8 text-red-100 max-w-2xl">
            The fastest way to discover your rarest Pokemon
          </motion.p>

          <motion.div variants={itemVariants} className="flex flex-col sm:flex-row gap-4 justify-center">
            <button className="px-8 py-4 bg-pokemon-yellow text-pokemon-red font-bold rounded-lg hover:bg-yellow-400 transition-all duration-300 shadow-lg">
              Download Now
            </button>
            <button className="px-8 py-4 border-2 border-white text-white font-bold rounded-lg hover:bg-white hover:text-pokemon-red transition-all duration-300">
              Learn More
            </button>
          </motion.div>

          <motion.div variants={itemVariants} className="mt-12 text-red-100">
            <p className="text-sm">✓ Trusted by 150K+ Pokemon trainers</p>
          </motion.div>
        </motion.div>
      </div>

      {/* Scroll Indicator */}
      <motion.div
        className="absolute bottom-10 left-1/2 -translate-x-1/2"
        animate={{ y: [0, 10, 0] }}
        transition={{ duration: 2, repeat: Infinity }}
      >
        <div className="w-6 h-10 border-2 border-white rounded-full flex justify-center">
          <div className="w-1 h-2 bg-white rounded-full mt-2"></div>
        </div>
      </motion.div>
    </section>
  )
}
