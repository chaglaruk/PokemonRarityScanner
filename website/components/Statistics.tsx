'use client'

import { motion } from 'framer-motion'
import { useState, useEffect } from 'react'

export default function Statistics() {
  const [counts, setCounts] = useState({ users: 0, scans: 0, accuracy: 0, speed: 0 })

  useEffect(() => {
    const intervals = [
      setInterval(() => setCounts((c) => ({ ...c, users: Math.min(c.users + 3000, 150000) })), 30),
      setInterval(() => setCounts((c) => ({ ...c, scans: Math.min(c.scans + 50000, 2500000) })), 30),
      setInterval(() => setCounts((c) => ({ ...c, accuracy: Math.min(c.accuracy + 1, 99) })), 50),
    ]

    return () => intervals.forEach(clearInterval)
  }, [])

  const stats = [
    { label: 'Active Users', value: `${(counts.users / 1000).toFixed(0)}K+`, icon: '👥' },
    { label: 'Pokemon Scanned', value: `${(counts.scans / 1000000).toFixed(1)}M+`, icon: '📱' },
    { label: 'Accuracy Rate', value: `${counts.accuracy}%`, icon: '🎯' },
    { label: 'Avg Scan Speed', value: '<2s', icon: '⚡' },
  ]

  return (
    <section className="section-container bg-pokemon-gray">
      <div className="grid md:grid-cols-4 gap-8">
        {stats.map((stat, index) => (
          <motion.div
            key={index}
            initial={{ opacity: 0, scale: 0.9 }}
            whileInView={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.6, delay: index * 0.1 }}
            viewport={{ once: true }}
            className="text-center"
          >
            <div className="text-5xl mb-4">{stat.icon}</div>
            <p className="text-4xl md:text-5xl font-heading font-bold text-pokemon-red mb-2">{stat.value}</p>
            <p className="text-gray-700 font-medium">{stat.label}</p>
          </motion.div>
        ))}
      </div>
    </section>
  )
}
