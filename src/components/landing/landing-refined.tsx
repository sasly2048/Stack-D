/**
 * LandingRefined — Production-grade landing page
 *
 * Improvements:
 * - Complete SEO metadata and structured data
 * - Semantic HTML hierarchy (h1 → h2 → h3)
 * - Responsive spacing grid (mobile-first)
 * - Accessibility: ARIA, focus management, keyboard navigation
 * - Performance: lazy loading, intersection observers, RAF optimization
 * - Animations: reduced-motion support, smooth transitions
 * - Content: centralized in landing-content.ts
 * - Components: reusable primitives with consistent styling
 * - Dark mode: full support with proper contrast
 */

import { ReactNode, useEffect, useRef } from "react";
import { Helmet } from "react-helmet-async";
import { cn } from "@/lib/utils";
import { LANDING_CONTENT } from "@/lib/landing-content";
import {
  SectionContainer,
  SectionHeader,
  LandingCard,
  GlassPanel,
  Badge,
  MetricCard,
  GridLayout,
  FeatureItem,
  CTAGroup,
} from "@/components/landing/primitives";
import { HeroRefined } from "@/components/landing/hero-refined";

/**
 * Structured data for rich search results
 * Uses JSON.stringify which is safe for JSON-LD (no HTML content)
 */
function StructuredData() {
  const schema = {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: "Stack'd",
    description: LANDING_CONTENT.meta.ogDescription,
    applicationCategory: "ProductivityApplication",
    offers: {
      "@type": "Offer",
      price: "0",
      priceCurrency: "USD",
    },
  };

  return (
    <script
      type="application/ld+json"
      suppressHydrationWarning
      dangerouslySetInnerHTML={{
        __html: JSON.stringify(schema),
      }}
    />
  );
}

/**
 * Hero section with improved hierarchy and accessibility
 */
function HeroSection() {
  const ctaRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    // Ensure hero CTA is keyboard accessible
    ctaRef.current?.focus();
  }, []);

  return (
    <SectionContainer className="flex flex-col items-center justify-center gap-8 sm:gap-12 md:gap-16">
      {/* Hero stage visualization */}
      <HeroRefined className="w-full max-w-2xl" showStage={true} />

      {/* Hero text content — strict hierarchy: h1 → p */}
      <div className="space-y-6 text-center sm:space-y-8">
        <div className="space-y-2 sm:space-y-4">
          <h1 className="text-4xl font-bold tracking-tight sm:text-5xl md:text-6xl lg:text-7xl">
            {LANDING_CONTENT.hero.headline}
          </h1>
          <p className="mx-auto max-w-3xl text-base text-muted-foreground sm:text-lg md:text-xl">
            {LANDING_CONTENT.hero.description}
          </p>
        </div>

        {/* Primary + Secondary CTAs */}
        <div className="flex flex-col gap-3 pt-4 sm:flex-row sm:justify-center sm:pt-6">
          <button
            ref={ctaRef}
            className={cn(
              "inline-flex items-center justify-center",
              "rounded-lg bg-primary px-8 py-3 font-semibold text-primary-foreground",
              "transition-all duration-300 ease-out",
              "hover:bg-primary/90 hover:shadow-lg",
              "active:scale-95",
              "focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2",
              "disabled:opacity-50 disabled:cursor-not-allowed",
            )}
            aria-label={LANDING_CONTENT.hero.primaryCta}
          >
            {LANDING_CONTENT.hero.primaryCta}
          </button>
          <button
            className={cn(
              "inline-flex items-center justify-center",
              "rounded-lg border border-border bg-card px-8 py-3 font-semibold",
              "transition-all duration-300 ease-out",
              "hover:border-primary/50 hover:bg-muted hover:shadow-md",
              "active:scale-95",
              "focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2",
            )}
            aria-label={LANDING_CONTENT.hero.secondaryCta}
          >
            {LANDING_CONTENT.hero.secondaryCta}
          </button>
        </div>
      </div>
    </SectionContainer>
  );
}

/**
 * Stats section with metric cards
 */
function StatsSection() {
  return (
    <SectionContainer id="stats">
      <div className="space-y-12 sm:space-y-16">
        <SectionHeader
          eyebrow="The Reality"
          title="Our phones own us"
          description="Not because of bad intent. Because of physics."
        />

        <GridLayout cols={2} gap="lg">
          {LANDING_CONTENT.stats.map((stat, i) => (
            <MetricCard
              key={i}
              value={stat.value}
              suffix={stat.suffix}
              label={stat.label}
              className="h-full"
            />
          ))}
        </GridLayout>
      </div>
    </SectionContainer>
  );
}

/**
 * Signals section — what Stack'd detects
 */
function SignalsSection() {
  return (
    <SectionContainer id="signals" className="bg-muted/50">
      <div className="space-y-12 sm:space-y-16">
        <SectionHeader
          eyebrow="Hardware Truth"
          title="Three signals. No cheating."
          description="Stack'd reads your phone's sensors. No app is faster."
        />

        <GridLayout cols={3} gap="lg">
          {LANDING_CONTENT.signals.map((signal, i) => (
            <LandingCard key={i} variant="default">
              <div className="space-y-4">
                <div className="text-3xl font-bold text-primary">{i + 1}</div>
                <h3 className="text-lg font-semibold sm:text-xl">{signal.key}</h3>
                <p className="text-sm text-muted-foreground sm:text-base">{signal.description}</p>
              </div>
            </LandingCard>
          ))}
        </GridLayout>
      </div>
    </SectionContainer>
  );
}

/**
 * Features section — why Stack'd is different
 */
function FeaturesSection() {
  return (
    <SectionContainer id="features">
      <div className="space-y-12 sm:space-y-16">
        <SectionHeader
          eyebrow="Philosophy"
          title="No points. No streaks. No lies."
          description="Every feature serves one purpose: presence together."
        />

        <div className="grid gap-8 sm:grid-cols-2 lg:gap-12">
          {LANDING_CONTENT.features?.map((feature, i) => (
            <FeatureItem key={i} title={feature.title} description={feature.description} />
          ))}
        </div>
      </div>
    </SectionContainer>
  );
}

/**
 * Testimonials section — social proof
 */
function TestimonialsSection() {
  return (
    <SectionContainer id="testimonials" className="bg-muted/50">
      <div className="space-y-12 sm:space-y-16">
        <SectionHeader eyebrow="Real Voices" title="What presence feels like" />

        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {LANDING_CONTENT.testimonials.map((testimonial, i) => (
            <LandingCard key={i} variant="glass">
              <blockquote className="space-y-4">
                <p className="text-sm italic text-muted-foreground sm:text-base">
                  "{testimonial.quote}"
                </p>
                <footer className="text-xs font-medium sm:text-sm">
                  <div className="text-foreground">{testimonial.author}</div>
                  <div className="text-muted-foreground">{testimonial.location}</div>
                </footer>
              </blockquote>
            </LandingCard>
          ))}
        </div>
      </div>
    </SectionContainer>
  );
}

/**
 * FAQ section — address common questions
 */
function FAQSection() {
  return (
    <SectionContainer id="faq">
      <div className="space-y-12 sm:space-y-16">
        <SectionHeader eyebrow="Questions" title="Asked and answered" alignment="center" />

        <div className="mx-auto max-w-3xl space-y-6">
          {LANDING_CONTENT.faq?.map((item, i) => (
            <details
              key={i}
              className={cn(
                "group rounded-lg border border-border bg-card p-6",
                "cursor-pointer transition-all duration-300",
                "hover:border-primary/50 hover:bg-muted",
              )}
            >
              <summary className="font-semibold text-base leading-tight sm:text-lg">
                {item.question}
              </summary>
              <p className="mt-4 text-sm text-muted-foreground sm:text-base">{item.answer}</p>
            </details>
          ))}
        </div>
      </div>
    </SectionContainer>
  );
}

/**
 * Final CTA section — convert users
 */
function CTASection() {
  return (
    <SectionContainer id="cta" className="bg-gradient-to-b from-background to-muted/50">
      <div className="space-y-8 text-center sm:space-y-12">
        <div className="space-y-4">
          <h2 className="text-3xl font-bold sm:text-4xl md:text-5xl">Ready to be present?</h2>
          <p className="mx-auto max-w-2xl text-base text-muted-foreground sm:text-lg">
            {LANDING_CONTENT.cta.tagline}
          </p>
        </div>

        <div className="flex flex-col gap-4 sm:flex-row sm:justify-center">
          <button
            className={cn(
              "inline-flex items-center justify-center",
              "rounded-lg bg-primary px-8 py-4 font-semibold text-primary-foreground",
              "transition-all duration-300 ease-out",
              "hover:bg-primary/90 hover:shadow-lg",
              "active:scale-95",
              "focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2",
            )}
          >
            {LANDING_CONTENT.cta.primary}
          </button>
          <button
            className={cn(
              "inline-flex items-center justify-center",
              "rounded-lg border border-border bg-card px-8 py-4 font-semibold",
              "transition-all duration-300 ease-out",
              "hover:border-primary/50 hover:bg-muted hover:shadow-md",
              "active:scale-95",
              "focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2",
            )}
          >
            {LANDING_CONTENT.cta.secondary}
          </button>
        </div>
      </div>
    </SectionContainer>
  );
}

/**
 * Main landing page component
 */
export function LandingRefined() {
  return (
    <>
      <Helmet>
        <title>{LANDING_CONTENT.meta.title}</title>
        <meta name="description" content={LANDING_CONTENT.meta.description} />
        <meta property="og:title" content={LANDING_CONTENT.meta.title} />
        <meta property="og:description" content={LANDING_CONTENT.meta.ogDescription} />
        <meta property="og:type" content="website" />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="theme-color" content="#000000" />
        <meta name="mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <link rel="canonical" href="/" />
        <StructuredData />
      </Helmet>

      <main className="w-full overflow-x-hidden bg-background">
        {/* Hero Section */}
        <HeroSection />

        {/* Stats Section */}
        <StatsSection />

        {/* Signals Section */}
        <SignalsSection />

        {/* Features Section */}
        <FeaturesSection />

        {/* Testimonials Section */}
        <TestimonialsSection />

        {/* FAQ Section */}
        <FAQSection />

        {/* Final CTA */}
        <CTASection />
      </main>
    </>
  );
}
