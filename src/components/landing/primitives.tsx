import { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { DESIGN_TOKENS } from "@/lib/design-tokens";

/**
 * SectionContainer — Wraps landing page sections with responsive padding and max-width
 * Ensures consistent spacing and readability across breakpoints
 */
export function SectionContainer({
  children,
  className,
  id,
}: {
  children: ReactNode;
  className?: string;
  id?: string;
}) {
  return (
    <section
      id={id}
      className={cn(
        // Responsive padding: mobile → tablet → desktop
        "px-4 py-12 sm:px-6 sm:py-16 md:px-8 md:py-24 lg:px-12 lg:py-32",
        // Centered, constrained max-width with left/right auto margins
        "mx-auto w-full",
        className,
      )}
    >
      {children}
    </section>
  );
}

/**
 * SectionHeader — Standardized section title + description
 * Ensures visual hierarchy and consistent spacing
 */
export function SectionHeader({
  eyebrow,
  title,
  description,
  alignment = "center",
  className,
}: {
  eyebrow?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  alignment?: "center" | "left";
  className?: string;
}) {
  return (
    <div
      className={cn(
        "space-y-2 sm:space-y-3 md:space-y-4",
        alignment === "center" && "text-center",
        className,
      )}
    >
      {eyebrow && (
        <div className="text-xs font-medium uppercase tracking-widest text-muted-foreground">
          {eyebrow}
        </div>
      )}
      <h2 className="text-3xl font-bold tracking-tight sm:text-4xl md:text-5xl lg:text-6xl">
        {title}
      </h2>
      {description && (
        <p className="mx-auto max-w-2xl text-base text-muted-foreground sm:text-lg md:text-xl">
          {description}
        </p>
      )}
    </div>
  );
}

/**
 * LandingCard — Reusable card component for features, testimonials, etc.
 * Ensures consistent borders, shadows, padding across all card types
 */
export function LandingCard({
  children,
  className,
  variant = "default",
  interactive = false,
}: {
  children: ReactNode;
  className?: string;
  variant?: "default" | "glass" | "minimal";
  interactive?: boolean;
}) {
  const variantStyles = {
    default: "border border-border bg-card",
    glass: "border border-white/10 bg-white/5 backdrop-blur-xl",
    minimal: "border-b border-border bg-transparent",
  };

  return (
    <div
      className={cn(
        "rounded-lg p-6 sm:p-8",
        variantStyles[variant],
        interactive && "transition-all duration-300 hover:border-primary/50 hover:shadow-lg",
        className,
      )}
    >
      {children}
    </div>
  );
}

/**
 * GlassPanel — Glass morphism panel with backdrop blur
 * Used for overlays, callouts, and highlighted content
 */
export function GlassPanel({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "rounded-xl border border-white/10 bg-white/5 p-6 backdrop-blur-xl sm:p-8",
        className,
      )}
    >
      {children}
    </div>
  );
}

/**
 * Badge — Semantic badge for labels, tags, status
 */
export function Badge({
  children,
  variant = "default",
  className,
}: {
  children: ReactNode;
  variant?: "default" | "primary" | "success" | "warning" | "error";
  className?: string;
}) {
  const variantStyles = {
    default: "bg-muted text-muted-foreground border border-border",
    primary: "bg-primary/10 text-primary border border-primary/20",
    success: "bg-green-100 text-green-800 border border-green-200",
    warning: "bg-yellow-100 text-yellow-800 border border-yellow-200",
    error: "bg-red-100 text-red-800 border border-red-200",
  };

  return (
    <span
      className={cn(
        "inline-block rounded-full px-3 py-1 text-xs font-medium",
        variantStyles[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}

/**
 * MetricCard — Displays a single metric with value, label, and optional visual
 */
export function MetricCard({
  value,
  label,
  suffix = "",
  icon,
  trend,
  className,
}: {
  value: string | number;
  label: string;
  suffix?: string;
  icon?: ReactNode;
  trend?: "up" | "down" | "neutral";
  className?: string;
}) {
  return (
    <LandingCard className={cn("text-center", className)}>
      {icon && <div className="mb-4 flex justify-center">{icon}</div>}
      <div className="space-y-2">
        <p className="text-4xl font-bold sm:text-5xl">
          {value}
          {suffix && <span className="text-lg">{suffix}</span>}
        </p>
        <p className="text-sm text-muted-foreground">{label}</p>
      </div>
    </LandingCard>
  );
}

/**
 * Eyebrow — Decorative text above section headers
 */
export function Eyebrow({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "inline-block text-xs font-medium uppercase tracking-widest text-muted-foreground",
        className,
      )}
    >
      {children}
    </div>
  );
}

/**
 * TextHighlight — Highlights key words or phrases in text
 */
export function TextHighlight({ children }: { children: ReactNode }) {
  return (
    <span className="bg-gradient-to-r from-primary to-primary/50 bg-clip-text text-transparent">
      {children}
    </span>
  );
}

/**
 * GridLayout — Responsive grid container with automatic gap handling
 */
export function GridLayout({
  children,
  cols = 3,
  gap = "lg",
  className,
}: {
  children: ReactNode;
  cols?: 1 | 2 | 3 | 4;
  gap?: "sm" | "md" | "lg" | "xl";
  className?: string;
}) {
  const gapStyles = {
    sm: "gap-4",
    md: "gap-6",
    lg: "gap-8",
    xl: "gap-12",
  };

  const colStyles = {
    1: "grid-cols-1",
    2: "grid-cols-1 sm:grid-cols-2",
    3: "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3",
    4: "grid-cols-1 sm:grid-cols-2 lg:grid-cols-4",
  };

  return <div className={cn("grid", colStyles[cols], gapStyles[gap], className)}>{children}</div>;
}

/**
 * FeatureGrid — Grid layout specifically for feature cards
 */
export function FeatureGrid({ children }: { children: ReactNode }) {
  return (
    <GridLayout cols={2} gap="lg">
      {children}
    </GridLayout>
  );
}

/**
 * FeatureItem — Individual feature in FeatureGrid
 */
export function FeatureItem({
  icon,
  title,
  description,
  className,
}: {
  icon?: ReactNode;
  title: ReactNode;
  description: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("space-y-3", className)}>
      {icon && <div className="text-2xl">{icon}</div>}
      <h3 className="text-lg font-semibold sm:text-xl">{title}</h3>
      <p className="text-sm text-muted-foreground sm:text-base">{description}</p>
    </div>
  );
}

/**
 * CTA Button group — Primary + Secondary call-to-action buttons
 */
export function CTAGroup({
  primary,
  secondary,
  onPrimary,
  onSecondary,
  className,
}: {
  primary: ReactNode;
  secondary: ReactNode;
  onPrimary?: () => void;
  onSecondary?: () => void;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col gap-3 sm:flex-row sm:justify-center", className)}>
      <button
        onClick={onPrimary}
        className="rounded-lg bg-primary px-8 py-3 font-medium text-primary-foreground transition-all duration-300 hover:bg-primary/90 hover:shadow-lg"
      >
        {primary}
      </button>
      <button
        onClick={onSecondary}
        className="rounded-lg border border-border bg-card px-8 py-3 font-medium transition-all duration-300 hover:border-primary/50 hover:bg-muted"
      >
        {secondary}
      </button>
    </div>
  );
}
