import { useEffect, useRef, useState } from "react";

import { haptic } from "@/lib/haptics";
import { useLowPower } from "@/hooks/use-low-power";
import { playEliteSfx } from "@/lib/celebration-sfx";

/**
 * ELITE celebration — "Optimize Your Focus". Motif: ascension / ignition.
 * A dark hold breaks with an ignition flash, then a dense field of rising
 * embers, expanding concentric light rings, and a molten "ELITE" crest that
 * forms and shimmers. Substantially larger than Pro in every dimension:
 * particle density, duration (~6s), lighting, and a 5-beat sequence:
 *   ignite → storm → crest → shimmer → settle.
 *
 * Shares NO animation code with Pro — different particle system (rising ember
 * field vs Pro's sigil), different palette emphasis, different sound.
 */
type Beat = "ignite" | "storm" | "crest" | "settle";

export function CelebrateElite({ open, onClose }: { open: boolean; onClose: () => void }) {
  const lowPower = useLowPower();
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [beat, setBeat] = useState<Beat>("ignite");

  // Rising-ember particle field. Denser and longer-lived than any Pro effect.
  useEffect(() => {
    if (!open || lowPower) return;
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = window.innerWidth * dpr;
    canvas.height = window.innerHeight * dpr;
    ctx.scale(dpr, dpr);
    const W = window.innerWidth;
    const H = window.innerHeight;

    const colors = ["#f0a968", "#ffc48a", "#c9874a", "#ffffff"];
    // Dense field — ~280 embers (Pro uses none; the plain success used 120).
    const embers = Array.from({ length: 280 }, () => {
      const fromCenter = Math.random() < 0.5;
      return {
        x: fromCenter ? W / 2 + (Math.random() - 0.5) * 120 : Math.random() * W,
        y: fromCenter ? H / 2 : H + Math.random() * 40,
        vx: (Math.random() - 0.5) * 1.4,
        vy: -(0.6 + Math.random() * 2.6),
        size: 1 + Math.random() * 3.5,
        color: colors[Math.floor(Math.random() * colors.length)],
        life: 1,
        decay: 0.004 + Math.random() * 0.006,
        flicker: Math.random() * Math.PI,
      };
    });

    // Expanding light rings at ignition.
    const rings: { r: number; life: number }[] = [];
    let ringTimer = 0;

    let raf = 0;
    let frame = 0;
    const render = () => {
      frame++;
      ctx.clearRect(0, 0, W, H);

      // Concentric rings — three, staggered.
      if (frame % 8 === 0 && ringTimer < 3) {
        rings.push({ r: 20, life: 1 });
        ringTimer++;
      }
      for (const ring of rings) {
        ring.r += 7;
        ring.life -= 0.012;
        if (ring.life <= 0) continue;
        ctx.beginPath();
        ctx.arc(W / 2, H / 2, ring.r, 0, Math.PI * 2);
        ctx.strokeStyle = `rgba(240,169,104,${Math.max(0, ring.life * 0.5)})`;
        ctx.lineWidth = 2;
        ctx.stroke();
      }

      for (const e of embers) {
        e.x += e.vx;
        e.y += e.vy;
        e.vy *= 0.996; // slight drag, embers hang
        e.flicker += 0.3;
        e.life -= e.decay;
        if (e.life <= 0) continue;
        const a = Math.max(0, e.life) * (0.6 + 0.4 * Math.sin(e.flicker));
        ctx.globalAlpha = a;
        ctx.fillStyle = e.color;
        ctx.shadowBlur = 8;
        ctx.shadowColor = e.color;
        ctx.beginPath();
        ctx.arc(e.x, e.y, e.size, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
      ctx.shadowBlur = 0;

      if (frame < 360) raf = requestAnimationFrame(render);
    };
    raf = requestAnimationFrame(render);
    return () => cancelAnimationFrame(raf);
  }, [open, lowPower]);

  // 5-beat timeline.
  useEffect(() => {
    if (!open) return;
    setBeat("ignite");
    haptic("heavy");
    playEliteSfx();
    const t1 = setTimeout(() => setBeat("storm"), 500);
    const t2 = setTimeout(() => {
      setBeat("crest");
      haptic("success");
    }, 1500);
    const t3 = setTimeout(() => setBeat("settle"), 3200);
    return () => [t1, t2, t3].forEach(clearTimeout);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  if (!open) return null;

  const showCrest = beat === "crest" || beat === "settle";

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-live="polite"
      aria-label="Elite membership activated"
      className="fixed inset-0 z-[120] flex items-center justify-center overflow-hidden bg-obsidian backdrop-blur-lg animate-fade-in"
      onClick={onClose}
    >
      {/* Ignition flash — a bright bloom that fades fast. */}
      {!lowPower && (
        <div
          className="pointer-events-none absolute inset-0"
          style={{ animation: "elite-ignite 0.9s ease-out forwards" }}
        />
      )}
      {/* Radial molten glow, richer than Pro's. */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse at center, rgba(255,196,138,0.28) 0%, rgba(240,169,104,0.14) 28%, rgba(201,135,74,0.05) 50%, transparent 70%)",
          animation: lowPower ? "none" : "ceremony-glow 3s ease-out",
        }}
      />

      <canvas ref={canvasRef} className="pointer-events-none absolute inset-0" aria-hidden="true" />

      <div
        className="relative flex flex-col items-center px-6 text-center"
        onClick={(e) => e.stopPropagation()}
        style={{
          opacity: showCrest ? 1 : 0,
          transform: showCrest ? "translateY(0) scale(1)" : "translateY(16px) scale(0.94)",
          transition: lowPower ? "none" : "all 0.7s cubic-bezier(0.32,0.72,0,1)",
        }}
      >
        <p className="font-mono text-[10px] tracking-[0.5em] uppercase text-ember-glow">
          Access granted
        </p>
        {/* Molten crest wordmark with a shimmer sweep */}
        <p
          className="mt-3 font-serif text-6xl md:text-7xl leading-none"
          style={{
            backgroundImage:
              "linear-gradient(100deg, #c9874a 0%, #f0a968 35%, #ffffff 50%, #ffc48a 65%, #c9874a 100%)",
            backgroundSize: "200% 100%",
            WebkitBackgroundClip: "text",
            backgroundClip: "text",
            color: "transparent",
            animation: lowPower ? "none" : "elite-shimmer 2.4s ease-in-out infinite",
          }}
        >
          Elite
        </p>
        <p className="mt-4 max-w-sm text-sm text-silver-dim">
          The intelligence layer is yours. Atlas, forecasting, adaptive sessions and the vault —
          everything Stack&apos;d can do, optimized around you.
        </p>

        <button
          onClick={onClose}
          className="mt-8 rounded-full border border-ember-glow/60 bg-ember/10 px-7 py-2.5 font-mono text-[11px] uppercase tracking-[0.2em] text-ember-glow transition hover:bg-ember/20"
          style={{
            opacity: beat === "settle" ? 1 : 0,
            pointerEvents: beat === "settle" ? "auto" : "none",
            transition: lowPower ? "none" : "opacity 0.6s ease-out 0.2s",
          }}
        >
          Optimize your focus
        </button>
      </div>
    </div>
  );
}
