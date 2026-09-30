import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import { requireFeature } from "@/lib/require-tier";
import { fetchMyPrivateProfile } from "./private-profile.server";

export interface DnaProfile {
  archetype: string;
  traits: { label: string; value: number }[]; // 0-100
  peakHour: number;
  consistencyScore: number;
  totalSessions: number;
  signature: string; // 6-char code
  /** Composite, dynamic personality, e.g. "Deep Worker • Night Owl". */
  personality: string | null;
}

/** Hour-of-day (0-23) and calendar day (YYYY-MM-DD) in a given IANA timezone. */
function inZone(iso: string, tz: string): { hour: number; day: string } {
  try {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: tz,
      hour12: false,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
    }).formatToParts(new Date(iso));
    const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "";
    // en-CA gives 24h "24" for midnight in some engines; normalize.
    const hour = Number(get("hour")) % 24;
    return { hour, day: `${get("year")}-${get("month")}-${get("day")}` };
  } catch {
    // Bad tz → fall back to UTC rather than throw.
    const d = new Date(iso);
    return { hour: d.getUTCHours(), day: iso.slice(0, 10) };
  }
}

export const getProductivityDna = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .inputValidator((d: { tz?: string } | undefined) => ({
    tz: typeof d?.tz === "string" && d.tz.length < 64 ? d.tz : "UTC",
  }))
  .handler(async ({ data, context }): Promise<DnaProfile> => {
    await requireFeature(context.supabase, "focus_dna");
    const tz = data.tz;
    const since = new Date(Date.now() - 60 * 24 * 3600 * 1000).toISOString();
    const { data: histData } = await context.supabase
      .from("focus_history")
      .select("score, duration_seconds, breaches_count, created_at, tier")
      .eq("profile_id", context.userId)
      .gte("created_at", since)
      .limit(500);
    const rows = histData ?? [];
    const total = rows.length || 1;

    const avgScore = rows.reduce((s, r) => s + (r.score as number), 0) / total;
    const avgDur = rows.reduce((s, r) => s + (r.duration_seconds as number), 0) / total;
    const perfect = rows.filter((r) => (r.breaches_count as number) === 0).length;
    const flow = rows.filter((r) => (r.score as number) >= 95).length;

    // Peak hour + active days computed in the USER's timezone, not UTC — a
    // 9pm-IST session must read as 21:00 local, not 15:30 UTC.
    const hours = new Array(24).fill(0);
    const days = new Set<string>();
    for (const r of rows) {
      const { hour, day } = inZone(r.created_at as string, tz);
      hours[hour]++;
      days.add(day);
    }
    const peakHour = hours.indexOf(Math.max(...hours));

    // Consistency: how evenly sessions are spread across days.
    const consistencyScore = Math.min(100, Math.round((days.size / 60) * 100));

    const traits = [
      { label: "Depth", value: Math.min(100, Math.round((avgDur / 3600) * 100)) },
      { label: "Precision", value: Math.round(avgScore) },
      { label: "Discipline", value: Math.round((perfect / total) * 100) },
      { label: "Flow", value: Math.round((flow / total) * 100) },
      { label: "Consistency", value: consistencyScore },
      { label: "Volume", value: Math.min(100, Math.round((total / 60) * 100)) },
    ];

    // Archetype from the profile's SHAPE, not a single highest score. Take the
    // top two traits and only treat the second as defining if it's genuinely
    // close to the first (within 15 pts) and itself meaningful (>= 40) — so a
    // clearly one-dimensional profile still reads as a single archetype, while a
    // balanced one gets a composite ("The Diver, Precise").
    const ranked = [...traits].sort((a, b) => b.value - a.value);
    const archetypeMap: Record<string, string> = {
      Depth: "The Diver",
      Precision: "The Marksman",
      Discipline: "The Monk",
      Flow: "The Channeler",
      Consistency: "The Metronome",
      Volume: "The Marathoner",
    };
    const modifierMap: Record<string, string> = {
      Depth: "Deep",
      Precision: "Precise",
      Discipline: "Disciplined",
      Flow: "In-Flow",
      Consistency: "Steady",
      Volume: "Relentless",
    };
    const primary = archetypeMap[ranked[0].label] ?? "The Wanderer";
    const second = ranked[1];
    const composite =
      second && second.value >= 40 && ranked[0].value - second.value <= 15
        ? `${primary}, ${modifierMap[second.label] ?? ""}`.trim()
        : primary;
    const archetype = composite;

    // 6-char signature from trait bytes
    const sig = traits
      .map((t) => t.value.toString(36).padStart(2, "0").slice(-1).toUpperCase())
      .join("");

    const privateProfile = await fetchMyPrivateProfile(context.supabase);

    return {
      personality: privateProfile?.productivity_dna ?? null,
      archetype,
      traits,
      peakHour,
      consistencyScore,
      totalSessions: rows.length,
      signature: sig,
    };
  });
