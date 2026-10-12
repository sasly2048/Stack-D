/**
 * The phone stack — the app's one recurring object (Android StackArt parity):
 * face-down phones with an ember rim and a camera bump, fanned slightly, one
 * per person at the table. `breathing` adds the slow living pulse used on Home
 * and during a live stack.
 */
export function StackArt({
  phones = 4,
  size = 150,
  breathing = false,
  className = "",
}: {
  phones?: number;
  size?: number;
  breathing?: boolean;
  className?: string;
}) {
  const n = Math.max(1, Math.min(6, phones));
  const w = size * 0.56;
  const h = size;
  return (
    <div
      aria-hidden="true"
      className={`relative ${breathing ? "animate-breathing" : ""} ${className}`}
      style={{ width: w + 24, height: h + 24 }}
    >
      {Array.from({ length: n }, (_, k) => n - 1 - k).map((i) => (
        <div
          key={i}
          className="absolute left-1/2 top-1/2 rounded-[18%/11%] border shadow-[0_24px_48px_-20px_rgba(0,0,0,0.9)]"
          style={{
            width: w,
            height: h,
            marginLeft: -w / 2,
            marginTop: -h / 2,
            transform: `translateY(${-i * 5}px) rotate(${(i - (n - 1) / 2) * 3.5}deg)`,
            borderColor: i === 0 ? "rgba(240,169,104,0.75)" : "rgba(240,169,104,0.32)",
            background:
              "linear-gradient(135deg, #1D1915 0%, #0E0C0A 55%, #15120F 100%), linear-gradient(115deg, transparent 40%, rgba(255,255,255,0.06) 50%, transparent 60%)",
            backgroundBlendMode: "screen",
          }}
        >
          {i === 0 && (
            <div
              className="absolute rounded-[30%] border border-white/10 bg-black/40"
              style={{ left: "10%", top: "6%", width: w * 0.26, height: w * 0.4 }}
            >
              <span className="absolute left-1/2 top-[18%] size-[34%] -translate-x-1/2 rounded-full border border-white/15 bg-black" />
              <span className="absolute left-1/2 top-[56%] size-[34%] -translate-x-1/2 rounded-full border border-white/15 bg-black" />
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

/** A thin ember ring around the stack — today's goal on Home, the timer live. */
export function EmberRing({
  progress,
  size = 260,
  stroke = 4,
}: {
  progress: number;
  size?: number;
  stroke?: number;
}) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-hidden="true"
      className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 -rotate-90"
    >
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke="rgba(237,230,218,0.08)"
        strokeWidth={stroke}
      />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke="var(--color-ember)"
        strokeWidth={stroke}
        strokeLinecap="round"
        strokeDasharray={c}
        strokeDashoffset={c * (1 - Math.max(0, Math.min(1, progress)))}
        style={{ transition: "stroke-dashoffset 1.2s var(--ease-ritual)" }}
      />
    </svg>
  );
}
