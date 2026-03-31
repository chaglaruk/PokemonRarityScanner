'use client'

import { motion } from 'framer-motion'

export default function DownloadCTA() {
  return (
    <section className="relative py-20 md:py-32 overflow-hidden" id="download">
      {/* Gradient Background */}
      <div className="absolute inset-0 gradient-pokemon opacity-95"></div>

      {/* Animated Elements */}
      <div className="absolute inset-0 overflow-hidden">
        <motion.div
          className="absolute -top-40 -right-40 w-80 h-80 rounded-full border border-white opacity-10"
          animate={{ rotate: 360 }}
          transition={{ duration: 30, repeat: Infinity, ease: 'linear' }}
        />
        <motion.div
          className="absolute -bottom-40 -left-40 w-80 h-80 rounded-full border border-white opacity-10"
          animate={{ rotate: -360 }}
          transition={{ duration: 30, repeat: Infinity, ease: 'linear' }}
        />
      </div>

      <div className="section-container relative z-10 text-center text-white">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          viewport={{ once: true }}
        >
          <h2 className="text-5xl md:text-6xl font-heading font-bold mb-4">
            150K+ Trainers are already scanning
          </h2>

          <p className="text-2xl text-red-100 mb-12 max-w-2xl mx-auto">
            Join the fastest-growing Pokemon rarity community
          </p>

          <div className="flex flex-col sm:flex-row gap-4 justify-center mb-12">
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="px-8 py-4 bg-pokemon-yellow text-pokemon-red font-heading font-bold rounded-lg hover:bg-yellow-400 transition-all shadow-lg flex items-center justify-center gap-2"
            >
              <span>📱</span> Google Play Store
            </motion.button>
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="px-8 py-4 border-2 border-white text-white font-heading font-bold rounded-lg hover:bg-white hover:text-pokemon-red transition-all"
            >
              <span>🍎</span> App Store (Coming Soon)
            </motion.button>
          </div>

          <motion.div
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            transition={{ delay: 0.3 }}
            viewport={{ once: true }}
            className="space-y-3 text-red-100"
          >
            <p className="text-sm">✓ Free forever with basic features</p>
            <p className="text-sm">✓ Premium features available ($2.99/month)</p>
            <p className="text-sm">✓ Web Dashboard & Desktop App coming soon</p>
          </motion.div>
        </motion.div>
      </div>
    </section>
  )
}
