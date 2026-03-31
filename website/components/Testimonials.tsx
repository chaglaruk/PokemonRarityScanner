'use client'

import { motion } from 'framer-motion'
import { useState } from 'react'

export default function Testimonials() {
  const [currentIndex, setCurrentIndex] = useState(0)

  const testimonials = [
    {
      quote: 'This app changed how I manage my collection. Instant rarity checks saved me from making terrible trades!',
      author: 'Alex T.',
      role: 'Pokemon GO Trainer',
      tag: 'Game Changer',
    },
    {
      quote: 'The accuracy is incredible. Every scan is spot-on. Definitely recommend to any serious collector.',
      author: 'Sarah M.',
      role: 'Collector',
      tag: 'Recommended',
    },
    {
      quote: 'So easy to use, even my kids can scan their Pokemon. We have family nights rating our collection now!',
      author: 'Mike R.',
      role: 'Parent',
      tag: 'Family Friendly',
    },
    {
      quote: 'The rarity algorithm is sophisticated. It considers everything I care about. Perfect for tournament prep.',
      author: 'Jordan K.',
      role: 'Competitive Player',
      tag: 'Professional Grade',
    },
    {
      quote: 'Fastest scan speed I\'ve ever seen. No lag, no waiting. Just instant results. Love it!',
      author: 'Casey D.',
      role: 'Power User',
      tag: 'Lightning Fast',
    },
  ]

  const next = () => setCurrentIndex((currentIndex + 1) % testimonials.length)
  const prev = () => setCurrentIndex((currentIndex - 1 + testimonials.length) % testimonials.length)

  return (
    <section className="section-container">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8 }}
        viewport={{ once: true }}
        className="text-center mb-16"
      >
        <h2 className="text-4xl md:text-5xl font-heading font-bold mb-4">Loved by Trainers Worldwide</h2>
        <p className="text-xl text-gray-600">Join thousands of happy users</p>
      </motion.div>

      {/* Testimonial Carousel */}
      <div className="max-w-3xl mx-auto">
        <motion.div
          key={currentIndex}
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -10 }}
          transition={{ duration: 0.5 }}
          className="bg-white border-2 border-pokemon-red rounded-2xl p-8 md:p-12"
        >
          <div className="flex gap-1 mb-4">
            {[...Array(5)].map((_, i) => (
              <span key={i} className="text-yellow-500 text-xl">
                ★
              </span>
            ))}
          </div>

          <p className="text-2xl font-medium text-pokemon-darkText mb-6 italic">
            "{testimonials[currentIndex].quote}"
          </p>

          <div className="flex items-center justify-between">
            <div>
              <p className="font-heading font-bold text-lg text-pokemon-darkText">{testimonials[currentIndex].author}</p>
              <p className="text-gray-600">{testimonials[currentIndex].role}</p>
            </div>
            <span className="px-4 py-2 bg-pokemon-yellow text-pokemon-darkText rounded-full font-semibold text-sm">
              {testimonials[currentIndex].tag}
            </span>
          </div>
        </motion.div>

        {/* Controls */}
        <div className="flex justify-center items-center gap-4 mt-8">
          <button
            onClick={prev}
            className="w-12 h-12 rounded-full border-2 border-pokemon-red text-pokemon-red hover:bg-pokemon-red hover:text-white transition-all"
          >
            ←
          </button>

          <div className="flex gap-2">
            {testimonials.map((_, index) => (
              <button
                key={index}
                onClick={() => setCurrentIndex(index)}
                className={`w-3 h-3 rounded-full transition-all ${
                  index === currentIndex ? 'bg-pokemon-red w-8' : 'bg-gray-300'
                }`}
              />
            ))}
          </div>

          <button
            onClick={next}
            className="w-12 h-12 rounded-full border-2 border-pokemon-red text-pokemon-red hover:bg-pokemon-red hover:text-white transition-all"
          >
            →
          </button>
        </div>
      </div>
    </section>
  )
}
