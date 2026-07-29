import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export interface DnaProfile {
  archetype: string;
  traits: { label: string; value: number }[]; // 0-100
  peakHour: number;
  consistencyScore: number;
  totalSessions: number;
  signature: string; // 6-char code
}

export const getProductivityDna = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<DnaProfile> => {
    const since = new Date(Date.now() - 60 * 24 * 3600 * 1000).toISOString();
    const { data } = await context.supabase
      .from("focus_history")
      .select("score, duration_seconds, breaches_count, created_at, tier")
      .eq("profile_id", context.userId)
      .gte("created_at", since)
      .limit(500);
    const rows = data ?? [];
    const total = rows.length || 1;

    const avgScore = rows.reduce((s, r) => s + (r.score as number), 0) / total;
    const avgDur = rows.reduce((s, r) => s + (r.duration_seconds as number), 0) / total;
    const perfect = rows.filter((r) => (r.breaches_count as number) === 0).length;
    const flow = rows.filter((r) => (r.score as number) >= 95).length;

    const hours = new Array(24).fill(0);
    for (const r of rows) hours[new Date(r.created_at as string).getUTCHours()]++;
    const peakHour = hours.indexOf(Math.max(...hours));

    // Consistency: how evenly sessions are spread across days
    const days = new Set(rows.map((r) => (r.created_at as string).slice(0, 10)));
    const consistencyScore = Math.min(100, Math.round((days.size / 60) * 100));

    const traits = [
      { label: "Depth", value: Math.min(100, Math.round((avgDur / 3600) * 100)) },
      { label: "Precision", value: Math.round(avgScore) },
      { label: "Discipline", value: Math.round((perfect / total) * 100) },
      { label: "Flow", value: Math.round((flow / total) * 100) },
      { label: "Consistency", value: consistencyScore },
      { label: "Volume", value: Math.min(100, Math.round((total / 60) * 100)) },
    ];

    // Archetype from strongest trait
    const top = [...traits].sort((a, b) => b.value - a.value)[0];
    const archetypeMap: Record<string, string> = {
      Depth: "The Diver",
      Precision: "The Marksman",
      Discipline: "The Monk",
      Flow: "The Channeler",
      Consistency: "The Metronome",
      Volume: "The Marathoner",
    };
    const archetype = archetypeMap[top.label] ?? "The Wanderer";

    // 6-char signature from trait bytes
    const sig = traits
      .map((t) => t.value.toString(36).padStart(2, "0").slice(-1).toUpperCase())
      .join("");

    return {
      archetype,
      traits,
      peakHour,
      consistencyScore,
      totalSessions: rows.length,
      signature: sig,
    };
  });
