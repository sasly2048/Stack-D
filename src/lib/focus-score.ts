// Focus Score engine — pure functions, no React or DB deps.
// Spec: S_focus = max(0, min(100, (T_focus/T_target)*100 - sum(P_breach)))
// XP   = floor(S_focus * (T_focus/60) * M_tier)
//
// Brief §3: durations flow through as fractional seconds (ms-precision) and
// are only floored at the *boundary* where we hand integers to Postgres.

export type Tier =
  | "flow"          // 95-100
  | "pristine"      // 85-94
  | "steady"        // 70-84
  | "fragmented"    // 40-69
  | "compromised";  // 0-39

export interface TierMeta {
  key: Tier;
  label: string;
  hex: string;
  multiplier: number;
}

export const TIERS: Record<Tier, TierMeta> = {
  flow:         { key: "flow",        label: "Flow State",          hex: "#06B6D4", multiplier: 1.5 },
  pristine:     { key: "pristine",    label: "Pristine Focus",      hex: "#10B981", multiplier: 1.0 },
  steady:       { key: "steady",      label: "Steady Ambient",      hex: "#F59E0B", multiplier: 0.5 },
  fragmented:   { key: "fragmented",  label: "Fragmented Attention",hex: "#F97316", multiplier: 0.0 },
  compromised:  { key: "compromised", label: "Protocol Compromised",hex: "#EF4444", multiplier: 0.0 },
};

export const ABANDONMENT_GRACE_SECONDS = 15;
export const MINOR_PENALTY = 10;
export const SEVERE_PENALTY = 40;

export function tierForScore(score: number): TierMeta {
  if (score >= 95) return TIERS.flow;
  if (score >= 85) return TIERS.pristine;
  if (score >= 70) return TIERS.steady;
  if (score >= 40) return TIERS.fragmented;
  return TIERS.compromised;
}

export interface BreachRecord { severity: "minor" | "severe" }

export interface ScoreInput {
  /** target in seconds (may be fractional) */
  targetSeconds: number;
  /** focused time in seconds (may be fractional / ms-precise) */
  focusSeconds: number;
  breaches: BreachRecord[];
  /** continuous time past grace window after a severe breach (fractional ok) */
  abandonmentSeconds?: number;
}

export interface ScoreResult {
  score: number;
  xp: number;
  tier: TierMeta;
  penalty: number;
  abandonmentPenalty: number;
  /** echoed back at integer second-precision for DB persistence */
  focusSecondsInt: number;
}

export function computeFocusScore({
  targetSeconds,
  focusSeconds,
  breaches,
  abandonmentSeconds = 0,
}: ScoreInput): ScoreResult {
  const target = Math.max(1, targetSeconds);
  const focus = Math.max(0, Math.min(focusSeconds, target));

  const breachPenalty = breaches.reduce(
    (sum, b) => sum + (b.severity === "severe" ? SEVERE_PENALTY : MINOR_PENALTY),
    0,
  );
  const abandonmentPenalty = Math.max(0, Math.floor(abandonmentSeconds - ABANDONMENT_GRACE_SECONDS));
  const penalty = breachPenalty + abandonmentPenalty;

  // Keep ms-precision through the multiplications — only round at the end.
  const raw = (focus / target) * 100 - penalty;
  const score = Math.max(0, Math.min(100, Math.round(raw)));
  const tier = tierForScore(score);
  const xp = Math.floor(score * (focus / 60) * tier.multiplier);

  return {
    score,
    xp,
    tier,
    penalty: breachPenalty,
    abandonmentPenalty,
    focusSecondsInt: Math.floor(focus),
  };
}
