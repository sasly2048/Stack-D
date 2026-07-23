/**
 * Lightweight skeletons for lazy-loaded FX layers.
 * Match the final layer's box so mounting the real component causes no
 * layout jump. Purely decorative — always aria-hidden.
 */

export function MapSkeleton({ className = "" }: { className?: string }) {
  // Mimics the dotted-map's aspect + faint dot grid so the swap is silent.
  return (
    <div
      aria-hidden="true"
      className={`relative w-full aspect-[76/34] rounded-xl overflow-hidden bg-white/[0.02] ${className}`}
    >
      <div
        className="absolute inset-0 opacity-40"
        style={{
          backgroundImage:
            "radial-gradient(circle, rgba(226,226,226,0.18) 1px, transparent 1.2px)",
          backgroundSize: "clamp(6px, 1.6vw, 12px) clamp(6px, 1.6vw, 12px)",
        }}
      />
      <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/[0.04] to-transparent animate-[skeleton-shimmer_2.4s_ease-in-out_infinite]" />
    </div>
  );
}

export function MeteorSkeleton() {
  // Meteors are decorative; the skeleton is a quiet static field so the
  // section reserves its stacking context without a visible flash.
  return (
    <div aria-hidden="true" className="absolute inset-0 pointer-events-none">
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,rgba(240,169,104,0.05),transparent_60%)]" />
    </div>
  );
}

export function ParallaxLayerSkeleton({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={`w-full h-full rounded-xl bg-white/[0.02] animate-[skeleton-shimmer_2.4s_ease-in-out_infinite] ${className}`}
    />
  );
}
