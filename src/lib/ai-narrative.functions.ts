import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

/**
 * AI narrative functions: pattern discovery, weekly story, group coach.
 * Uses Lovable AI Gateway with gemini-3.5-flash for speed.
 */

const GATEWAY_URL = "https://ai.gateway.lovable.dev/v1/chat/completions";

async function ai(prompt: string, system: string): Promise<string> {
  const key = process.env.LOVABLE_API_KEY;
  if (!key) throw new Error("Missing LOVABLE_API_KEY");
  const res = await fetch(GATEWAY_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Lovable-API-Key": key },
    body: JSON.stringify({
      model: "google/gemini-3.5-flash",
      messages: [{ role: "system", content: system }, { role: "user", content: prompt }],
    }),
  });
  if (!res.ok) throw new Error(`AI ${res.status}`);
  const j = await res.json();
  return j.choices?.[0]?.message?.content ?? "";
}

export const getWeeklyStory = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ story: string }> => {
    const since = new Date(Date.now() - 7 * 86400_000).toISOString();
    const { data: hist } = await context.supabase
      .from("focus_history")
      .select("duration_seconds,score,breaches_count,created_at")
      .eq("profile_id", context.userId)
      .gte("created_at", since);
    const rows = hist ?? [];
    if (rows.length === 0) return { story: "No sessions this week — start a Focus Block to build your story." };
    const totalMin = Math.round(rows.reduce((s, r) => s + (r.duration_seconds ?? 0), 0) / 60);
    const avg = Math.round(rows.reduce((s, r) => s + (r.score ?? 0), 0) / rows.length);
    const byDay = new Map<string, number>();
    for (const r of rows) {
      const d = new Date(r.created_at).toLocaleDateString([], { weekday: "long" });
      byDay.set(d, (byDay.get(d) ?? 0) + (r.duration_seconds ?? 0));
    }
    const strongest = [...byDay.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? "";
    const summary = `Sessions: ${rows.length}. Total minutes: ${totalMin}. Avg score: ${avg}. Strongest day: ${strongest}.`;
    const story = await ai(
      `Data:\n${summary}\n\nWrite a 3-sentence narrative recap. No stats-dump. Warm, poetic, decisive.`,
      "You are Stack'd, a focus companion. Reply in short poetic sentences, no bullet points.",
    );
    return { story: story.trim() };
  });

export const discoverPatterns = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ patterns: string[] }> => {
    const { data: hist } = await context.supabase
      .from("focus_history")
      .select("duration_seconds,score,created_at")
      .eq("profile_id", context.userId)
      .order("created_at", { ascending: false })
      .limit(60);
    const rows = hist ?? [];
    if (rows.length < 5) return { patterns: [] };
    // Buckets: night (20-4), morning (4-12), afternoon (12-20)
    const buckets = { night: [] as number[], morning: [] as number[], afternoon: [] as number[] };
    for (const r of rows) {
      const h = new Date(r.created_at).getHours();
      const b = h >= 20 || h < 4 ? "night" : h < 12 ? "morning" : "afternoon";
      buckets[b].push(r.score ?? 0);
    }
    const avg = (a: number[]) => (a.length ? a.reduce((s, n) => s + n, 0) / a.length : 0);
    const patterns: string[] = [];
    const entries = Object.entries(buckets).map(([k, v]) => [k, avg(v)] as const).filter(([, v]) => v > 0);
    entries.sort((a, b) => b[1] - a[1]);
    if (entries.length >= 2 && entries[0][1] - entries[1][1] > 8) {
      patterns.push(`You're strongest in the ${entries[0][0]} — ${Math.round(entries[0][1])} avg vs. ${Math.round(entries[1][1])}.`);
    }
    const streak = rows.filter((r) => (r.score ?? 0) >= 90).length;
    if (streak >= 3) patterns.push(`${streak} flow-tier sessions in your recent history.`);
    return { patterns };
  });
