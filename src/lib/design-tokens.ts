// Centralized design tokens for the landing page
// Replaces magic numbers and ensures consistency across all sections

export const DESIGN_TOKENS = {
  spacing: {
    xs: "0.25rem", // 4px
    sm: "0.5rem", // 8px
    md: "1rem", // 16px
    lg: "1.5rem", // 24px
    xl: "2rem", // 32px
    "2xl": "3rem", // 48px
    "3xl": "4rem", // 64px
    "4xl": "6rem", // 96px
    "5xl": "8rem", // 128px
  },

  containerWidth: {
    sm: "640px",
    md: "768px",
    lg: "1024px",
    xl: "1280px",
    "2xl": "1536px",
  },

  sectionPadding: {
    mobile: "2rem 1rem", // 32px vertical, 16px horizontal
    tablet: "4rem 2rem", // 64px vertical, 32px horizontal
    desktop: "6rem 3rem", // 96px vertical, 48px horizontal
  },

  gridGap: {
    sm: "1rem",
    md: "1.5rem",
    lg: "2rem",
    xl: "3rem",
  },

  typography: {
    fontFamily: {
      sans: "ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
      mono: "ui-monospace, 'Cascadia Code', 'Source Code Pro', Menlo, Consolas, 'Courier New', monospace",
    },

    fontSize: {
      xs: "0.75rem", // 12px
      sm: "0.875rem", // 14px
      base: "1rem", // 16px
      lg: "1.125rem", // 18px
      xl: "1.25rem", // 20px
      "2xl": "1.5rem", // 24px
      "3xl": "1.875rem", // 30px
      "4xl": "2.25rem", // 36px
      "5xl": "3rem", // 48px
      "6xl": "3.75rem", // 60px
      "7xl": "4.5rem", // 72px
    },

    lineHeight: {
      tight: 1.2,
      snug: 1.375,
      normal: 1.5,
      relaxed: 1.625,
      loose: 2,
    },

    fontWeight: {
      light: 300,
      normal: 400,
      medium: 500,
      semibold: 600,
      bold: 700,
      extrabold: 800,
    },

    letterSpacing: {
      tighter: "-0.05em",
      tight: "-0.025em",
      normal: "0",
      wide: "0.025em",
      wider: "0.05em",
      widest: "0.1em",
    },
  },

  colors: {
    // Primary brand palette
    primary: {
      50: "hsl(210, 100%, 95%)",
      100: "hsl(210, 100%, 85%)",
      200: "hsl(210, 100%, 75%)",
      300: "hsl(210, 100%, 60%)",
      400: "hsl(210, 100%, 45%)",
      500: "hsl(210, 100%, 30%)",
      600: "hsl(210, 100%, 20%)",
      700: "hsl(210, 100%, 15%)",
      800: "hsl(210, 100%, 10%)",
      900: "hsl(210, 100%, 5%)",
    },

    // Neutral palette
    neutral: {
      50: "hsl(0, 0%, 98%)",
      100: "hsl(0, 0%, 96%)",
      200: "hsl(0, 0%, 93%)",
      300: "hsl(0, 0%, 89%)",
      400: "hsl(0, 0%, 64%)",
      500: "hsl(0, 0%, 45%)",
      600: "hsl(0, 0%, 32%)",
      700: "hsl(0, 0%, 23%)",
      800: "hsl(0, 0%, 13%)",
      900: "hsl(0, 0%, 8%)",
    },

    // Semantic colors
    success: "hsl(142, 71%, 45%)",
    warning: "hsl(45, 93%, 47%)",
    error: "hsl(0, 84%, 60%)",
    info: "hsl(207, 89%, 55%)",
  },

  borderRadius: {
    none: "0",
    sm: "0.25rem", // 4px
    md: "0.375rem", // 6px
    lg: "0.5rem", // 8px
    xl: "0.75rem", // 12px
    "2xl": "1rem", // 16px
    "3xl": "1.5rem", // 24px
    full: "9999px",
  },

  shadow: {
    none: "none",
    sm: "0 1px 2px 0 rgba(0, 0, 0, 0.05)",
    md: "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1)",
    lg: "0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1)",
    xl: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)",
    "2xl": "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
  },

  animation: {
    // Durations (milliseconds)
    duration: {
      fast: 150,
      normal: 300,
      slow: 500,
      slower: 800,
    },

    // Easing functions
    easing: {
      linear: "linear",
      easeIn: "cubic-bezier(0.4, 0, 1, 1)",
      easeOut: "cubic-bezier(0, 0, 0.2, 1)",
      easeInOut: "cubic-bezier(0.4, 0, 0.2, 1)",
      easeOutQuad: "cubic-bezier(0, 0, 0.25, 1)",
      easeInQuad: "cubic-bezier(0.25, 0.46, 0.45, 0.94)",
      easeOutCubic: "cubic-bezier(0.33, 0.66, 0.66, 1)",
      easeInCubic: "cubic-bezier(0.32, 0, 0.67, 0.33)",
      spring: "cubic-bezier(0.34, 1.56, 0.64, 1)",
    },
  },

  parallax: {
    // Parallax intensity (higher = more movement)
    hero: 50,
    insight: 80,
    solution: 70,
    philosophy: 90,
    cta: 110,
  },

  breakpoints: {
    xs: 0,
    sm: 640,
    md: 768,
    lg: 1024,
    xl: 1280,
    "2xl": 1536,
  },

  // Responsive defaults
  responsive: {
    mobileFirst: true, // Mobile-first breakpoints
    mobileSectionGap: "1.5rem",
    tabletSectionGap: "2rem",
    desktopSectionGap: "3rem",
  },

  // Accessibility
  a11y: {
    focusOutlineWidth: "2px",
    focusOutlineOffset: "2px",
    minTouchTarget: "44px", // WCAG 2.5.5
    minTextContrast: "4.5:1", // WCAG AAA for normal text
  },

  // Performance
  performance: {
    reduceMotion: true, // Respect prefers-reduced-motion
    lazyLoadThreshold: "400px",
    rafThrottleMs: 16, // ~60fps
  },
};

export type DesignTokens = typeof DESIGN_TOKENS;

// Utility to get responsive spacing based on breakpoint
export function getResponsivePadding(breakpoint: "mobile" | "tablet" | "desktop"): string {
  const padding = DESIGN_TOKENS.sectionPadding;
  return padding[breakpoint];
}

// Utility to build animation transition string
export function transition(
  duration: keyof typeof DESIGN_TOKENS.animation.duration = "normal",
  easing: keyof typeof DESIGN_TOKENS.animation.easing = "easeOut",
): string {
  const dur = DESIGN_TOKENS.animation.duration[duration];
  const eas = DESIGN_TOKENS.animation.easing[easing];
  return `${dur}ms ${eas}`;
}
