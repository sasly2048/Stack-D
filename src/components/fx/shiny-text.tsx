export function ShinyText({
  children,
  className = "",
  speed = 5,
}: {
  children: React.ReactNode;
  className?: string;
  /** Seconds per full sweep. */
  speed?: number;
}) {
  // Linear timing on background-position is essential — cubic-bezier easing
  // on an infinite background-position sweep visibly stalls at each cycle
  // because the ease decelerates to ~zero before restarting. Linear keeps the
  // light moving at a constant rate with no perceptible stutter.
  return (
    <span
      className={`shiny-text ${className}`}
      style={{
        backgroundImage:
          "linear-gradient(110deg, rgba(226,226,226,0.55) 0%, rgba(226,226,226,0.55) 40%, #FFFFFF 50%, rgba(226,226,226,0.55) 60%, rgba(226,226,226,0.55) 100%)",
        backgroundSize: "220% 100%",
        WebkitBackgroundClip: "text",
        backgroundClip: "text",
        color: "transparent",
        animation: `shiny-sweep ${speed}s linear infinite`,
        willChange: "background-position",
      }}
    >
      {children}
    </span>
  );
}
