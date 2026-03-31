import Header from '@/components/Header'
import Hero from '@/components/Hero'
import ValueProp from '@/components/ValueProp'
import FeaturesGrid from '@/components/FeaturesGrid'
import HowItWorks from '@/components/HowItWorks'
import Testimonials from '@/components/Testimonials'
import Statistics from '@/components/Statistics'
import FAQ from '@/components/FAQ'
import DownloadCTA from '@/components/DownloadCTA'
import Footer from '@/components/Footer'

export default function Home() {
  return (
    <main className="w-full overflow-x-hidden">
      <Header />
      <Hero />
      <ValueProp />
      <FeaturesGrid />
      <HowItWorks />
      <Testimonials />
      <Statistics />
      <FAQ />
      <DownloadCTA />
      <Footer />
    </main>
  )
}
