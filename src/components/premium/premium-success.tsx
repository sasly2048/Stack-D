import { useEffect, useRef } from "react";

/**
 * Full-screen premium confirmation, matching the session-ceremony idiom
 * (obsidian backdrop, radial ember glow, serif display). Renders when `open`.
 * Shows a light ember confetti burst — no library, just a short canvas
 * animation — plus a Done button. Respects reduced motion (skips confetti).
 */
export function PremiumSuccess({
  open,
  tierLabel,
  onClose,
}: {
  open: boolean;
  /** e.g. "Pro", "Elite", "Lifetime" — what they just unlocked. */
  tierLabel: string;
  onClose: () => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const reduce =
      typeof window !== "undefined" &&
      window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (reduce) return;

    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;

    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    // Ember-family palette so the burst reads as part of the product.
    const colors = ["#f0a968", "#ffc48a", "#c9874a", "#e2e2e2"];
    const pieces = Array.from({ length: 120 }, () => ({
      x: canvas.width / 2,
      y: canvas.height / 2,
      vx: Math.cos(Math.random() * Math.PI * 2) * (2 + Math.random() * 6),
      vy: Math.sin(Math.random() * Math.PI * 2) * (2 + Math.random() * 6) - 3,
      size: 3 + Math.random() * 4,
      color: colors[Math.floor(Math.random() * colors.length)],
      rot: Math.random() * Math.PI,
      vr: (Math.random() - 0.5) * 0.3,
      life: 1,
    }));

    let raf = 0;
    let frame = 0;
    const tick = () => {
      frame++;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      for (const p of pieces) {
        p.vy += 0.18; // gravity
        p.x += p.vx;
        p.y += p.vy;
        p.rot += p.vr;
        p.life -= 0.008;
        if (p.life <= 0) continue;
        ctx.save();
        ctx.globalAlpha = Math.max(0, p.life);
        ctx.translate(p.x, p.y);
        ctx.rotate(p.rot);
        ctx.fillStyle = p.color;
        ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size * 0.6);
        ctx.restore();
      }
      // ~2.2s of animation, then stop.
      if (frame < 140) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-live="polite"
      aria-label="Payment complete"
      className="fixed inset-0 z-[110] flex items-center justify-center overflow-y-auto bg-obsidian/90 backdrop-blur-xl animate-fade-in"
      onClick={onClose}
    >
      <canvas ref={canvasRef} className="pointer-events-none absolute inset-0" aria-hidden="true" />
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background:
            "radial-gradient(ellipse at center, rgba(240,169,104,0.22) 0%, rgba(240,169,104,0.07) 32%, transparent 66%)",
          animation: "ceremony-glow 1.8s ease-out",
        }}
      />

      <div
        className="relative w-full max-w-sm px-6 py-16 text-center animate-scale-in"
        onClick={(e) => e.stopPropagation()}
      >
        <p className="font-mono text-[10px] tracking-[0.35em] uppercase text-ember">
          Payment confirmed
        </p>
        <p className="mt-4 font-serif text-4xl text-silver leading-tight">Welcome to {tierLabel}</p>
        <p className="mt-3 text-sm text-silver-dim">
          Your subscription is active. Everything&apos;s unlocked — no refresh needed.
        </p>
        <button
          onClick={onClose}
          className="mt-8 rounded-full border border-ember/50 px-6 py-2 font-mono text-[11px] uppercase tracking-widest text-ember transition hover:bg-ember/10"
        >
          Start exploring
        </button>
      </div>
    </div>
  );
}
