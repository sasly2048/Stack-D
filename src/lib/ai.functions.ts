import { createServerFn } from "@tanstack/react-start";
import { z } from "zod";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

/** In-memory TTL cache for public brand prose (per Worker instance). */
type Cached<T> = { value: T; expires: number };
const cache = new Map<string, Cached<unknown>>();
function getCached<T>(key: string): T | null {
  const hit = cache.get(key);
  if (!hit) return null;
  if (Date.now() > hit.expires) {
    cache.delete(key);
    return null;
  }
  return hit.value as T;
}
function setCached<T>(key: string, value: T, ttlMs: number) {
  cache.set(key, { value, expires: Date.now() + ttlMs });
}

/* -------------------------------------------------------------------------- */
/*  1. Public: dynamic AI-written brand prose for landing + philosophy pages  */
/* -------------------------------------------------------------------------- */

export type BrandProse = {
  landingKicker: string;
  landingLede: string;
  philosophyOpener: string;
  philosophyClosing: string;
  generatedAt: string;
};

const BRAND_FALLBACK: BrandProse = {
  landingKicker: "Signal held. Notifications silenced. Room online.",
  landingLede: "Every session is a small treaty against the feed — six characters, one room, and the steady proof of stillness.",
  philosophyOpener: "The attention economy is not a metaphor. It is a supply chain, and you were the yield.",
  philosophyClosing: "Presence is not restored. It is practiced.",
  generatedAt: new Date(0).toISOString(),
};

export const generateBrandProse = createServerFn({ method: "GET" }).handler(
  async (): Promise<BrandProse> => {
    const CACHE_KEY = "brand-prose-v1";
    const cached = getCached<BrandProse>(CACHE_KEY);
    if (cached) return cached;

    try {
      const { callAIJson, BRAND_TONE } = await import("./ai.server");
      const data = await callAIJson<Omit<BrandProse, "generatedAt">>({
        temperature: 1.0,
        messages: [
          { role: "system", content: BRAND_TONE },
          {
            role: "user",
            content: `Write four short passages for the Stack'd website. Return JSON with exactly these keys:

- "landingKicker": a single monospace-style line, max 60 characters, all caps or sentence case, feels like a system status. Example energy: "Signal held. Room online."
- "landingLede": one to two sentences (max 240 chars) placed under a "field data" statistics block on the landing page. Frame the numbers as evidence of what phones cost us.
- "philosophyOpener": exactly two sentences (max 260 chars) that open the philosophy page manifesto. Diagnose the attention economy without cliché.
- "philosophyClosing": a single ceremonial line (max 90 chars) that could be read aloud before a session.

Vary the language every time — do not repeat the sample line. Return only the JSON object.`,
          },
        ],
      });

      const cleaned: BrandProse = {
        landingKicker: String(data.landingKicker ?? BRAND_FALLBACK.landingKicker).slice(0, 80),
        landingLede: String(data.landingLede ?? BRAND_FALLBACK.landingLede).slice(0, 280),
        philosophyOpener: String(data.philosophyOpener ?? BRAND_FALLBACK.philosophyOpener).slice(0, 320),
        philosophyClosing: String(data.philosophyClosing ?? BRAND_FALLBACK.philosophyClosing).slice(0, 120),
        generatedAt: new Date().toISOString(),
      };
      setCached(CACHE_KEY, cleaned, 30 * 60 * 1000); // 30 min
      return cleaned;
    } catch {
      return BRAND_FALLBACK;
    }
  },
);

/* -------------------------------------------------------------------------- */
/*  2. Authenticated: next-session recommendation based on focus_history      */
/* -------------------------------------------------------------------------- */

export type SessionRecommendation = {
  durationMinutes: number;
  topic: string;
  rationale: string;
  confidence: "low" | "medium" | "high";
  basedOnSessions: number;
  generatedAt: string;
};

const RecommendationSchema = z.object({
  durationMinutes: z.number().int().min(10).max(180),
  topic: z.string().min(1).max(60),
  rationale: z.string().min(1).max(240),
  confidence: z.enum(["low", "medium", "high"]),
});

export const recommendNextSession = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<SessionRecommendation> => {
    const { supabase, userId } = context;

    const { data: hist } = await supabase
      .from("focus_history")
      .select("score, duration_seconds, breaches_count, tier, created_at")
      .eq("profile_id", userId)
      .order("created_at", { ascending: false })
      .limit(20);

    const rows = hist ?? [];
    const basedOnSessions = rows.length;

    if (rows.length === 0) {
      return {
        durationMinutes: 20,
        topic: "First stack — settle in",
        rationale: "No history yet. Start light: twenty minutes is long enough to feel the silence, short enough to complete cleanly.",
        confidence: "low",
        basedOnSessions: 0,
        generatedAt: new Date().toISOString(),
      };
    }

    const avgScore = Math.round(rows.reduce((a, r) => a + (r.score ?? 0), 0) / rows.length);
    const avgMin = Math.round(rows.reduce((a, r) => a + (r.duration_seconds ?? 0), 0) / rows.length / 60);
    const totalBreaches = rows.reduce((a, r) => a + (r.breaches_count ?? 0), 0);
    const recent = rows.slice(0, 8).map((r) => ({
      min: Math.round((r.duration_seconds ?? 0) / 60),
      score: r.score ?? 0,
      breaches: r.breaches_count ?? 0,
      tier: r.tier,
      when: new Date(r.created_at).toISOString().slice(0, 10),
    }));

    try {
      const { callAIJson, BRAND_TONE } = await import("./ai.server");
      const raw = await callAIJson<z.infer<typeof RecommendationSchema>>({
        temperature: 0.7,
        messages: [
          { role: "system", content: BRAND_TONE },
          {
            role: "user",
            content: `Recommend the next focus session for a Stack'd user, in JSON.

Stats over ${rows.length} recent sessions:
- avg score: ${avgScore}/100
- avg duration: ${avgMin} min
- total breaches: ${totalBreaches}
- last 8 sessions: ${JSON.stringify(recent)}

Rules:
- Pick "durationMinutes" from {15, 20, 25, 30, 45, 60, 90}. If the user is completing longer sessions cleanly (low breaches, high score), nudge up one tier. If breaches are frequent, nudge down.
- "topic" is a short focus theme (≤ 40 chars) framed in Stack'd's voice — a verb + noun feel. Examples: "Deep read, no tabs", "Draft the hard email", "One task, no queue".
- "rationale" is one to two sentences (≤ 200 chars) tying the recommendation to the pattern in their data. Be specific about the number.
- "confidence": "low" if < 5 sessions, "medium" if 5–14, "high" if 15+.

Return only the JSON.`,
          },
        ],
      });

      const parsed = RecommendationSchema.parse(raw);
      const tiers = [15, 20, 25, 30, 45, 60, 90];
      const snapped = tiers.reduce((best, t) =>
        Math.abs(t - parsed.durationMinutes) < Math.abs(best - parsed.durationMinutes) ? t : best,
        tiers[0],
      );

      return {
        durationMinutes: snapped,
        topic: parsed.topic.slice(0, 40),
        rationale: parsed.rationale,
        confidence: parsed.confidence,
        basedOnSessions,
        generatedAt: new Date().toISOString(),
      };
    } catch {
      const tier = avgScore >= 85 && totalBreaches < 3 ? 45 : avgScore >= 70 ? 30 : 20;
      return {
        durationMinutes: tier,
        topic: avgScore >= 80 ? "Deep work, one task" : "Rebuild the baseline",
        rationale: `Averaging ${avgScore}/100 across ${basedOnSessions} sessions at ${avgMin} min. ${
          avgScore >= 80 ? "Room to push longer." : "Shorter, cleaner runs first."
        }`,
        confidence: basedOnSessions >= 15 ? "high" : basedOnSessions >= 5 ? "medium" : "low",
        basedOnSessions,
        generatedAt: new Date().toISOString(),
      };
    }
  });

/* -------------------------------------------------------------------------- */
/*  3. Authenticated: personalized dashboard insights (brand-tone paragraphs) */
/* -------------------------------------------------------------------------- */

export type DashboardInsights = {
  paragraphs: string[];        // 2–3 brand-tone paragraphs
  headline: string;            // short kicker, ≤ 60 chars
  basedOnSessions: number;
  generatedAt: string;
};

const InsightsSchema = z.object({
  headline: z.string().min(1).max(80),
  paragraphs: z.array(z.string().min(1).max(320)).min(2).max(3),
});

export const generateDashboardInsights = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<DashboardInsights> => {
    const { supabase, userId } = context;

    const { data: profile } = await supabase
      .from("profiles")
      .select("display_name, lifetime_xp, current_focus_streak")
      .eq("id", userId)
      .maybeSingle();

    const { data: hist } = await supabase
      .from("focus_history")
      .select("score, duration_seconds, breaches_count, tier, created_at")
      .eq("profile_id", userId)
      .order("created_at", { ascending: false })
      .limit(30);

    const rows = hist ?? [];
    const basedOnSessions = rows.length;
    const displayName = profile?.display_name ?? "Practitioner";

    if (rows.length === 0) {
      return {
        headline: "Ledger empty. That is the beginning, not a lack.",
        paragraphs: [
          "You have not yet stacked. The dashboard is a mirror — right now it reflects the possibility of a first session, nothing more.",
          "Start short. Twenty minutes is enough to feel the shape of unbroken attention, and enough to prove to yourself that the silence is survivable.",
        ],
        basedOnSessions: 0,
        generatedAt: new Date().toISOString(),
      };
    }

    // Analytical summary the model can reason over.
    const avgScore = Math.round(rows.reduce((a, r) => a + (r.score ?? 0), 0) / rows.length);
    const avgMin = Math.round(rows.reduce((a, r) => a + (r.duration_seconds ?? 0), 0) / rows.length / 60);
    const totalBreaches = rows.reduce((a, r) => a + (r.breaches_count ?? 0), 0);
    const cleanCount = rows.filter((r) => (r.breaches_count ?? 0) === 0).length;

    // Compare last 5 vs prior 5 for trend
    const last5 = rows.slice(0, 5);
    const prev5 = rows.slice(5, 10);
    const last5Avg = last5.length ? Math.round(last5.reduce((a, r) => a + (r.score ?? 0), 0) / last5.length) : avgScore;
    const prev5Avg = prev5.length ? Math.round(prev5.reduce((a, r) => a + (r.score ?? 0), 0) / prev5.length) : last5Avg;
    const trendDelta = last5Avg - prev5Avg;

    // Time-of-day pattern (best-scoring hour bucket)
    const buckets: Record<string, { sum: number; n: number }> = {};
    for (const r of rows) {
      const h = new Date(r.created_at).getHours();
      const bucket = h < 6 ? "night" : h < 12 ? "morning" : h < 18 ? "afternoon" : "evening";
      buckets[bucket] ??= { sum: 0, n: 0 };
      buckets[bucket].sum += r.score ?? 0;
      buckets[bucket].n += 1;
    }
    const bestBucket = Object.entries(buckets)
      .map(([k, v]) => ({ k, avg: v.sum / v.n, n: v.n }))
      .sort((a, b) => b.avg - a.avg)[0]?.k ?? "morning";

    try {
      const { callAIJson, BRAND_TONE } = await import("./ai.server");
      const raw = await callAIJson<z.infer<typeof InsightsSchema>>({
        temperature: 0.85,
        messages: [
          { role: "system", content: BRAND_TONE },
          {
            role: "user",
            content: `Write personalized dashboard insights for a Stack'd practitioner. Return JSON.

Practitioner: ${displayName}
Lifetime XP: ${profile?.lifetime_xp ?? 0}
Current streak: ${profile?.current_focus_streak ?? 0}
Sessions analyzed: ${rows.length}
Average score: ${avgScore}/100
Average duration: ${avgMin} min
Total breaches: ${totalBreaches}
Clean (no-breach) sessions: ${cleanCount} of ${rows.length}
Recent 5 avg: ${last5Avg}/100 (change vs prior 5: ${trendDelta >= 0 ? "+" : ""}${trendDelta})
Best-scoring window: ${bestBucket}

Return:
- "headline": one line, ≤ 60 chars, in Stack'd voice — an observation, not a compliment.
- "paragraphs": exactly 2 short paragraphs (60–110 words total combined). Each paragraph is 2–3 sentences. Reference specific numbers from the data. The first paragraph names what the data shows. The second paragraph names what to do next.

Do not use the practitioner's name unless it flows naturally. Avoid "great job", "keep it up", emoji, and exclamation marks. This is a ledger, not a coach.`,
          },
        ],
      });

      const parsed = InsightsSchema.parse(raw);
      return {
        headline: parsed.headline.slice(0, 80),
        paragraphs: parsed.paragraphs.map((p) => p.slice(0, 340)),
        basedOnSessions,
        generatedAt: new Date().toISOString(),
      };
    } catch {
      const trendWord = trendDelta > 3 ? "sharpening" : trendDelta < -3 ? "slipping" : "steady";
      return {
        headline: `Ledger reads ${avgScore}/100 across ${basedOnSessions} sessions.`,
        paragraphs: [
          `Average duration sits at ${avgMin} minutes with ${totalBreaches} total anomalies. ${cleanCount} of ${basedOnSessions} sessions were clean — the discipline is ${trendWord}.`,
          bestBucket === "morning"
            ? "The mornings return the highest scores. Book that window before the day fragments it."
            : bestBucket === "evening"
            ? "Evenings hold the line more reliably than the rest of the day. Protect that block."
            : `The ${bestBucket} window scores best. Move a longer session into it and see if the pattern holds.`,
        ],
        basedOnSessions,
        generatedAt: new Date().toISOString(),
      };
    }
  });

/* -------------------------------------------------------------------------- */
/*  4. Authenticated: post-session AI recap for a specific completed run      */
/* -------------------------------------------------------------------------- */

export type SessionRecap = {
  title: string;                 // ≤ 60 chars
  summary: string;               // 2–3 sentence recap
  reflections: string[];         // 2–3 short lines
  nextStep: string;              // one line prompt for next session
  score: number;
  xp: number;
  durationSeconds: number;
  breachesCount: number;
  tier: string;
  roomCode: string;
  generatedAt: string;
};

const RecapSchema = z.object({
  title: z.string().min(1).max(80),
  summary: z.string().min(1).max(360),
  reflections: z.array(z.string().min(1).max(160)).min(2).max(3),
  nextStep: z.string().min(1).max(180),
});

export const generateSessionRecap = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((data: {
    roomId: string;
    score: number;
    xp: number;
    durationSeconds: number;
    breachesCount: number;
    tier: string;
    roomCode: string;
  }) => ({
    roomId: String(data.roomId),
    score: Math.max(0, Math.min(100, Math.round(Number(data.score) || 0))),
    xp: Math.max(0, Math.round(Number(data.xp) || 0)),
    durationSeconds: Math.max(0, Math.round(Number(data.durationSeconds) || 0)),
    breachesCount: Math.max(0, Math.round(Number(data.breachesCount) || 0)),
    tier: String(data.tier || "OFFERING").slice(0, 40),
    roomCode: String(data.roomCode || "").slice(0, 12).toUpperCase(),
  }))
  .handler(async ({ data, context }): Promise<SessionRecap> => {
    const { supabase, userId } = context;

    // Pull a few prior scores so the recap can compare (short, cheap).
    const { data: prior } = await supabase
      .from("focus_history")
      .select("score, duration_seconds, breaches_count, created_at")
      .eq("profile_id", userId)
      .order("created_at", { ascending: false })
      .limit(5);

    const priorRows = prior ?? [];
    const priorAvg = priorRows.length
      ? Math.round(priorRows.reduce((a, r) => a + (r.score ?? 0), 0) / priorRows.length)
      : 0;
    const delta = priorAvg ? data.score - priorAvg : 0;
    const mins = Math.round(data.durationSeconds / 60);

    const base = {
      score: data.score,
      xp: data.xp,
      durationSeconds: data.durationSeconds,
      breachesCount: data.breachesCount,
      tier: data.tier,
      roomCode: data.roomCode,
    };

    try {
      const { callAIJson, BRAND_TONE } = await import("./ai.server");
      const raw = await callAIJson<z.infer<typeof RecapSchema>>({
        temperature: 0.8,
        messages: [
          { role: "system", content: BRAND_TONE },
          {
            role: "user",
            content: `Write a session recap for a Stack'd focus block. Return JSON.

Session:
- Room: ${data.roomCode}
- Score: ${data.score}/100
- Tier: ${data.tier}
- Duration: ${mins} min
- Anomalies: ${data.breachesCount}
- XP earned: ${data.xp}
- Recent 5-session avg score (context, not always relevant): ${priorAvg}/100
- Change vs recent avg: ${delta >= 0 ? "+" : ""}${delta}

Return:
- "title": ≤ 60 chars, one line, name the session as if titling a chapter — descriptive, restrained.
- "summary": 2–3 sentences (max ~55 words) that describe what happened. Reference the score, duration, and anomalies as evidence. No praise, no scolding.
- "reflections": 2–3 short observations (≤ 20 words each). Each stands alone. One should address anomalies if there were any; skip that one if there were none.
- "nextStep": one line (≤ 30 words) suggesting the shape of the next session. Concrete, not motivational.

Voice is obsidian, ceremonial, restrained. No emoji. No exclamation marks.`,
          },
        ],
      });

      const parsed = RecapSchema.parse(raw);
      return {
        ...base,
        title: parsed.title.slice(0, 80),
        summary: parsed.summary,
        reflections: parsed.reflections.slice(0, 3),
        nextStep: parsed.nextStep,
        generatedAt: new Date().toISOString(),
      };
    } catch {
      const cleanRun = data.breachesCount === 0;
      return {
        ...base,
        title: cleanRun ? `${mins}-Minute Clean Stack` : `${mins}-Minute Session · ${data.breachesCount} anomalies`,
        summary: `${mins} minutes held at ${data.score}/100${
          priorAvg ? ` (${delta >= 0 ? "+" : ""}${delta} vs your recent average)` : ""
        }. ${cleanRun ? "The stack stayed intact." : `${data.breachesCount} anomalies logged.`} Earned ${data.xp} XP.`,
        reflections: cleanRun
          ? [
              "Zero anomalies — the room held its shape.",
              `Duration matched intent; the ${mins}-minute block completed cleanly.`,
              "Small, repeated cleanness compounds into ranks.",
            ]
          : [
              `${data.breachesCount} anomalies broke the surface; the shape is still forming.`,
              `The room held for the portion it did — treat that as the baseline.`,
              "Next session, shorten the target or move the phone further away.",
            ],
        nextStep: cleanRun
          ? `Repeat at ${mins} minutes, then push to the next tier once two runs in a row read clean.`
          : `Drop to ${Math.max(15, mins - 10)} minutes on the next stack and rebuild the streak first.`,
        generatedAt: new Date().toISOString(),
      };
    }
  });
