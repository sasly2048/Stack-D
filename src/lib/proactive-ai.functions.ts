import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export type ProactiveInsight = {
  smartSchedule: { hour: number; label: string; rationale: string } | null;
  focusPrediction: { nextScore: number; confidence: "low" | "medium" | "high"; note: string };
  burnout: { risk: "low" | "medium" | "high"; signals: string[]; recommendation: string };
  generatedAt: string;
};

const FALLBACK: ProactiveInsight = {
  smartSchedule: null,
  focusPrediction: { nextScore: 75, confidence: "low", note: "Not enough sessions yet — the model needs a week of data." },
  burnout: { risk: "low", signals: [], recommendation: "Keep the cadence steady. Rest days count as data." },
  generatedAt: new Date().toISOString(),
};

export const getProactiveInsights = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<ProactiveInsight> => {
    const { supabase, userId } = context;
    const since = new Date(Date.now() - 21 * 86400_000).toISOString();
    const { data: rows } = await supabase
      .from("focus_history")
      .select("score, duration_seconds, breaches_count, tier, created_at")
      .eq("profile_id", userId)
      .gte("created_at", since)
      .order("created_at", { ascending: false })
      .limit(200);

    const sessions = rows ?? [];
    if (sessions.length < 3) return { ...FALLBACK, generatedAt: new Date().toISOString() };

    // Best hour by avg score (min 2 sessions)
    const hourAgg = new Map<number, { sum: number; n: number }>();
    for (const s of sessions) {
      const h = new Date(s.created_at).getUTCHours();
      const cur = hourAgg.get(h) ?? { sum: 0, n: 0 };
      cur.sum += s.score;
      cur.n += 1;
      hourAgg.set(h, cur);
    }
    let bestHour: number | null = null;
    let bestAvg = -1;
    for (const [h, v] of hourAgg) {
      if (v.n >= 2 && v.sum / v.n > bestAvg) {
        bestAvg = v.sum / v.n;
        bestHour = h;
      }
    }

    // Trend: last 5 vs previous 5
    const recent = sessions.slice(0, 5);
    const prev = sessions.slice(5, 10);
    const avg = (xs: typeof sessions) => (xs.length ? xs.reduce((a, r) => a + r.score, 0) / xs.length : 0);
    const recentAvg = avg(recent);
    const prevAvg = avg(prev);
    const delta = recentAvg - prevAvg;
    const nextScore = Math.round(Math.max(0, Math.min(100, recentAvg + delta * 0.3)));
    const confidence: ProactiveInsight["focusPrediction"]["confidence"] =
      sessions.length >= 15 ? "high" : sessions.length >= 8 ? "medium" : "low";

    // Burnout signals
    const signals: string[] = [];
    const totalMinutes = sessions.reduce((s, r) => s + r.duration_seconds / 60, 0);
    const breachRate = sessions.reduce((s, r) => s + r.breaches_count, 0) / sessions.length;
    const scoreDropped = delta < -8;
    if (totalMinutes / 21 > 240) signals.push("Averaging over 4h focus/day for 3 weeks.");
    if (breachRate > 2) signals.push("Breach rate climbing — attention slipping mid-session.");
    if (scoreDropped) signals.push(`Recent scores down ${Math.abs(Math.round(delta))} points.`);
    const risk: ProactiveInsight["burnout"]["risk"] =
      signals.length >= 2 ? "high" : signals.length === 1 ? "medium" : "low";

    const dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    void dayNames;

    // Optional AI polish for copy — falls back to deterministic text
    let scheduleLabel = bestHour !== null ? `${String(bestHour).padStart(2, "0")}:00 UTC window` : "";
    let scheduleRationale = bestHour !== null ? `Your average score in this hour is ${Math.round(bestAvg)}.` : "";
    let burnoutRec =
      risk === "high"
        ? "Cut one session tomorrow. Sleep the deficit off."
        : risk === "medium"
          ? "Hold volume flat this week; recovery is compounding."
          : "Cadence is sustainable. Extend one block by 15 minutes.";
    let predictionNote =
      delta > 3
        ? "Momentum is up. Next session should extend the run."
        : delta < -3
          ? "Signal degrading. Consider a shorter, cleaner block."
          : "Stable. Repeat what worked.";

    try {
      const { callAIJson, BRAND_TONE } = await import("./ai.server");
      const summary = await callAIJson<{
        schedule?: string;
        prediction?: string;
        burnout?: string;
      }>({
        temperature: 0.6,
        messages: [
          { role: "system", content: BRAND_TONE },
          {
            role: "user",
            content: `Return JSON with keys "schedule", "prediction", "burnout" — each 1 short sentence.
              Data: bestHourUTC=${bestHour}, bestHourAvg=${Math.round(bestAvg)}, recentAvg=${Math.round(recentAvg)}, delta=${Math.round(delta)}, burnoutRisk=${risk}, signals=${JSON.stringify(signals)}.`,
          },
        ],
      });
      if (summary.schedule) scheduleRationale = summary.schedule;
      if (summary.prediction) predictionNote = summary.prediction;
      if (summary.burnout) burnoutRec = summary.burnout;
    } catch {
      // fall back to deterministic copy
    }

    return {
      smartSchedule: bestHour !== null ? { hour: bestHour, label: scheduleLabel, rationale: scheduleRationale } : null,
      focusPrediction: { nextScore, confidence, note: predictionNote },
      burnout: { risk, signals, recommendation: burnoutRec },
      generatedAt: new Date().toISOString(),
    };
  });
