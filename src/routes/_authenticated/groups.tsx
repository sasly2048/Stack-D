import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";
import { Nav } from "@/components/nav";
import { QueryBoundary, SkeletonList } from "@/components/query-states";
import { BadgeHint } from "@/components/ui/badge-hint";
import { INTERACTIVE, INTERACTIVE_TIGHT, ROW_INTERACTIVE } from "@/components/ui/interactive";
import { generateRoomCode } from "@/lib/room";
import { publishGroupSprint } from "@/lib/invite-channel";

export const Route = createFileRoute("/_authenticated/groups")({
  head: () => ({
    meta: [
      { title: "Focus Circles — Stack'd" },
      { name: "description", content: "Create and manage shared Stack'd focus circles, pool collective XP and hold silence with your group." },
      { property: "og:title", content: "Focus Circles — Stack'd" },
      { property: "og:description", content: "Create and manage shared Stack'd focus circles, pool collective XP and hold silence with your group." },
    ],
  }),
  component: GroupsPage,
});

interface GroupRow {
  id: string;
  name: string;
  created_by: string;
  total_group_xp: number;
  created_at: string;
}
interface MemberRow {
  group_id: string;
  profile_id: string;
  profiles: { display_name: string; lifetime_xp: number } | null;
}

function GroupsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [openGroup, setOpenGroup] = useState<string | null>(null);
  const [sprintBusy, setSprintBusy] = useState(false);
  // Validate the circle name only once the field has been left, so the form
  // never turns red while the first character is being typed.
  const [nameTouched, setNameTouched] = useState(false);
  const nameInvalid = nameTouched && name.trim().length === 0;
  // Which join/leave is mid-flight, so only that row's button disables — a
  // single global `busy` greyed out every row on the page.
  const [rowBusy, setRowBusy] = useState<string | null>(null);
  // Per-group cooldowns: groupId -> { until: epoch ms, total: seconds }
  const [cooldowns, setCooldowns] = useState<Record<string, { until: number; total: number }>>({});
  const [nowTick, setNowTick] = useState(() => Date.now());

  useEffect(() => {
    const hasActive = Object.values(cooldowns).some((c) => c.until > Date.now());
    if (!hasActive) return;
    const id = window.setInterval(() => setNowTick(Date.now()), 250);
    return () => window.clearInterval(id);
  }, [cooldowns]);

  // Was a hand-rolled `load()` that discarded every Supabase error and had no
  // loading state, so a failure rendered as "no circles exist" and a slow
  // network rendered as the same thing. Brought onto react-query to match the
  // rest of the app's four-state handling.
  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: async () => {
      const { data: u } = await supabase.auth.getUser();
      if (!u.user) throw new Error("Not signed in");
      const { data: p } = await supabase
        .from("profiles")
        .select("display_name")
        .eq("id", u.user.id)
        .maybeSingle();

      const { data: gs, error: gsErr } = await supabase
        .from("focus_groups")
        .select("*")
        .order("total_group_xp", { ascending: false });
      if (gsErr) throw gsErr;

      const ids = (gs ?? []).map((g) => g.id);
      const { data: ms, error: msErr } = ids.length
        ? await supabase
            .from("group_members")
            .select("group_id, profile_id, profiles(display_name, lifetime_xp)")
            .in("group_id", ids)
        : { data: [], error: null };
      if (msErr) throw msErr;

      return {
        me: { id: u.user.id, name: p?.display_name ?? "You" },
        groups: (gs ?? []) as GroupRow[],
        members: (ms ?? []) as unknown as MemberRow[],
      };
    },
  });

  const me = groupsQuery.data?.me ?? null;
  const groups = groupsQuery.data?.groups ?? [];
  const members = groupsQuery.data?.members ?? [];
  const load = async () => {
    await queryClient.invalidateQueries({ queryKey: ["groups"] });
  };

  const myGroupIds = useMemo(
    () => new Set(members.filter((m) => m.profile_id === me?.id).map((m) => m.group_id)),
    [members, me?.id],
  );

  const create = async () => {
    if (!me || !name.trim()) return;
    setBusy(true);
    const { data: g, error } = await supabase
      .from("focus_groups")
      .insert({ name: name.trim(), created_by: me.id })
      .select()
      .single();
    setBusy(false);
    if (error || !g) {
      toast.error(error?.message ?? "Could not create circle");
      return;
    }
    await supabase.from("group_members").insert({ group_id: g.id, profile_id: me.id });
    setName("");
    toast.success("Circle forged");
    load();
  };

  const join = async (groupId: string) => {
    if (!me) return;
    setRowBusy(groupId);
    const { error } = await supabase
      .from("group_members")
      .insert({ group_id: groupId, profile_id: me.id });
    if (error) {
      setRowBusy(null);
      toast.error(error.message);
      return;
    }
    // Joining and leaving were the only mutations on this page with no
    // confirmation at all — the row just quietly swapped its button.
    toast.success("Joined circle");
    await load();
    setRowBusy(null);
  };

  const leave = async (groupId: string) => {
    if (!me) return;
    setRowBusy(groupId);
    await supabase.from("group_members").delete().eq("group_id", groupId).eq("profile_id", me.id);
    toast.success("Left circle");
    await load();
    setRowBusy(null);
  };

  const startGroupSprint = async (group: GroupRow) => {
    if (!me) return;
    setSprintBusy(true);
    try {
      let code = generateRoomCode();
      for (let i = 0; i < 5; i++) {
        const { data: existing } = await supabase
          .from("rooms")
          .select("id")
          .eq("code", code)
          .maybeSingle();
        if (!existing) break;
        code = generateRoomCode();
      }
      const { data: room, error } = await supabase
        .from("rooms")
        .insert({
          code,
          host_id: me.id,
          target_duration_seconds: 30 * 60,
          status: "lobby",
        })
        .select()
        .single();
      if (error || !room) throw error ?? new Error("Could not create room");
      await supabase
        .from("participants")
        .insert({ room_id: room.id, user_id: me.id, display_name: me.name });

      const expiresAt = Date.now() + 5 * 60 * 1000;
      const {
        error: pubErr,
        rateLimited,
        retryAfterSeconds,
      } = await publishGroupSprint({
        groupId: group.id,
        roomId: room.id,
        roomCode: room.code,
        expiresAt,
      });
      if (pubErr) {
        if (rateLimited) {
          const secs = retryAfterSeconds ?? 60;
          const until = Date.now() + secs * 1000;
          const nextAt = new Date(until);
          const when = nextAt.toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
          });
          setCooldowns((c) => ({ ...c, [group.id]: { until, total: secs } }));
          toast.error("Dispatch rate-limited", {
            description: `Too many sprint invites just now. Try again in ~${secs}s (around ${when}).`,
            duration: Math.min(secs * 1000, 8000),
          });
          // Roll back the just-created room so we don't leak an orphan lobby.
          await supabase.from("rooms").delete().eq("id", room.id);
          return;
        }
        throw pubErr;
      }

      const memberCount = members.filter(
        (m) => m.group_id === group.id && m.profile_id !== me.id,
      ).length;
      toast.success(`Sprint dispatched to ${memberCount} member${memberCount === 1 ? "" : "s"}`);
      navigate({ to: "/room/$code", params: { code: room.code } });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Sprint failed");
    } finally {
      setSprintBusy(false);
    }
  };

  // Leaderboards
  const groupBoard = useMemo(() => {
    return groups
      .map((g) => {
        const ms = members.filter((m) => m.group_id === g.id);
        const avg = ms.length
          ? Math.round(ms.reduce((a, m) => a + (m.profiles?.lifetime_xp ?? 0), 0) / ms.length)
          : 0;
        return { group: g, members: ms.length, avg };
      })
      .sort((a, b) => b.avg - a.avg);
  }, [groups, members]);

  const personalBoard = useMemo(() => {
    const map = new Map<string, { name: string; xp: number }>();
    for (const m of members) {
      if (!m.profiles) continue;
      map.set(m.profile_id, { name: m.profiles.display_name, xp: m.profiles.lifetime_xp });
    }
    return Array.from(map.values())
      .sort((a, b) => b.xp - a.xp)
      .slice(0, 20);
  }, [members]);

  return (
    <div className="min-h-screen bg-obsidian text-silver">
      <Nav />
      <main className="pt-nav pb-20 px-6 max-w-5xl mx-auto">
        <div className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-2">
          CIRCLES / LEADERBOARDS
        </div>
        <h1 className="text-5xl font-extrabold tracking-tighter mb-12">Focus circles.</h1>

        <section className="mb-14">
          <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
            CREATE_CIRCLE
            {/* The asterisk is decorative; the word is what gets announced. */}
            <span aria-hidden="true" className="ml-1 text-ember">
              *
            </span>
            <span className="sr-only"> (required)</span>
          </h2>
          <div className="flex gap-3">
            <input
              id="circle-name-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              onBlur={() => setNameTouched(true)}
              aria-label="Circle name (required)"
              aria-invalid={nameInvalid || undefined}
              aria-describedby={nameInvalid ? "circle-name-hint" : "circle-name-count"}
              placeholder="Circle name (e.g. Dev Team)"
              maxLength={80}
              className="flex-1 bg-white/5 border border-white/10 rounded-lg px-4 py-3 text-sm placeholder:text-muted-foreground focus:outline-none focus:border-silver/50"
            />
            <button
              onClick={create}
              disabled={busy || !name.trim()}
              aria-busy={busy}
              className={`bg-silver text-obsidian px-6 py-3 rounded-lg font-mono text-xs uppercase tracking-widest font-bold hover:invert transition-all disabled:opacity-40 ${INTERACTIVE}`}
            >
              Forge
            </button>
          </div>
          {nameInvalid && (
            <p
              id="circle-name-hint"
              role="alert"
              className="mt-1.5 font-mono text-[10px] tracking-wide text-breach"
            >
              Give the circle a name before forging it.
            </p>
          )}
          {/* A disabled Forge with no stated reason is a dead end; the counter
              only appears near the cap so it isn't noise the whole time. */}
          {!nameInvalid && !name.trim() && (
            <p
              aria-live="polite"
              className="mt-1.5 font-mono text-[10px] uppercase tracking-widest text-muted-foreground"
            >
              Name the circle to forge it
            </p>
          )}
          {name.length > 60 && (
            <p
              id="circle-name-count"
              aria-live="polite"
              className="mt-1.5 font-mono text-[10px] uppercase tracking-widest text-muted-foreground"
            >
              {80 - name.length} characters left
            </p>
          )}
        </section>

        <section className="grid md:grid-cols-2 gap-8 mb-14">
          <div>
            <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
              ALL_CIRCLES{groupsQuery.isPending ? "" : ` · ${groups.length}`}
            </h2>
            <div className="space-y-3">
              <QueryBoundary
                isPending={groupsQuery.isPending}
                isError={groupsQuery.isError}
                error={groupsQuery.error}
                onRetry={() => groupsQuery.refetch()}
                errorTitle="Couldn't load circles."
                loadingLabel="Loading circles"
                skeleton={<SkeletonList rows={3} />}
                isEmpty={groups.length === 0}
                empty={
                  /* "None yet." stated the problem and stopped. The action that
                     resolves it is the form directly above, so point at it. */
                  <div className="p-4 border border-white/10 rounded-lg bg-white/[0.02]">
                    <p className="font-mono text-xs text-muted-foreground">None yet.</p>
                    <button
                      type="button"
                      onClick={() => {
                        document.getElementById("circle-name-input")?.focus();
                      }}
                      className={`mt-3 border border-silver/30 px-3 py-1.5 rounded font-mono text-[10px] uppercase tracking-widest hover:bg-silver hover:text-obsidian ${INTERACTIVE_TIGHT}`}
                    >
                      Forge the first circle
                    </button>
                  </div>
                }
              >
              {groups.map((g) => {
                const ms = members.filter((m) => m.group_id === g.id);
                const isMember = myGroupIds.has(g.id);
                const isOwner = me?.id === g.created_by;
                const cd = cooldowns[g.id];
                const cdActive = !!cd && cd.until > nowTick;
                const cdRemainingMs = cdActive ? cd.until - nowTick : 0;
                const cdRemainingSec = Math.ceil(cdRemainingMs / 1000);
                const cdPct = cdActive
                  ? Math.max(
                      0,
                      Math.min(100, ((cd.total * 1000 - cdRemainingMs) / (cd.total * 1000)) * 100),
                    )
                  : 100;
                const cdReadyAt = cdActive
                  ? new Date(cd.until).toLocaleTimeString([], {
                      hour: "2-digit",
                      minute: "2-digit",
                      second: "2-digit",
                    })
                  : null;
                return (
                  <div key={g.id} className="p-4 border border-white/10 rounded-lg bg-white/[0.02]">
                    <div className="flex items-center justify-between mb-2">
                      {/* The whole name block is the expand target, not just
                          the text: a 2-line row with a 1-word hit area is the
                          most common mis-click on this screen. */}
                      <button
                        onClick={() => setOpenGroup(openGroup === g.id ? null : g.id)}
                        aria-expanded={openGroup === g.id}
                        aria-label={`${g.name} — ${openGroup === g.id ? "hide" : "show"} members`}
                        className={`flex-1 min-w-0 -m-2 p-2 rounded-lg text-left ${ROW_INTERACTIVE} cursor-pointer active:scale-[0.99]`}
                      >
                        <div className="flex items-center gap-2">
                          <div className="font-medium truncate">{g.name}</div>
                          {/* Membership and ownership are both real columns, so
                              these say something the row otherwise hides. */}
                          {isOwner ? (
                            <BadgeHint tone="accent" title="You created this circle">
                              Owner
                            </BadgeHint>
                          ) : isMember ? (
                            <BadgeHint tone="positive" title="You are a member of this circle">
                              Joined
                            </BadgeHint>
                          ) : null}
                        </div>
                        <div className="font-mono text-[10px] text-muted-foreground uppercase tracking-widest">
                          {ms.length} member{ms.length === 1 ? "" : "s"} · {g.total_group_xp} XP
                        </div>
                      </button>
                      <div className="flex gap-2">
                        {isMember ? (
                          <>
                            <button
                              onClick={() => startGroupSprint(g)}
                              disabled={sprintBusy || cdActive}
                              aria-live="polite"
                              aria-label={
                                cdActive
                                  ? `Rate limited. Retry in ${cdRemainingSec} seconds`
                                  : "Start sprint"
                              }
                              className={`relative overflow-hidden bg-silver text-obsidian px-3 py-1.5 rounded font-mono text-[10px] uppercase tracking-widest font-bold hover:invert transition-all disabled:opacity-40 disabled:hover:filter-none min-w-[110px] ${INTERACTIVE_TIGHT}`}
                            >
                              {cdActive && (
                                <span
                                  aria-hidden
                                  className="absolute inset-0 bg-breach/30 origin-left transition-transform duration-200 ease-linear"
                                  style={{ transform: `scaleX(${(100 - cdPct) / 100})` }}
                                />
                              )}
                              <span className="relative">
                                {cdActive ? `Retry ${cdRemainingSec}s` : "Start Sprint"}
                              </span>
                            </button>
                            {!isOwner && (
                              <button
                                onClick={() => leave(g.id)}
                                disabled={rowBusy === g.id}
                                aria-busy={rowBusy === g.id}
                                className={`border border-breach/40 text-breach px-3 py-1.5 rounded font-mono text-[10px] uppercase tracking-widest hover:bg-breach hover:text-obsidian transition-all ${INTERACTIVE_TIGHT}`}
                              >
                                Leave
                              </button>
                            )}
                          </>
                        ) : (
                          <button
                            onClick={() => join(g.id)}
                            disabled={rowBusy === g.id}
                            aria-busy={rowBusy === g.id}
                            className={`border border-silver/30 px-3 py-1.5 rounded font-mono text-[10px] uppercase tracking-widest hover:bg-silver hover:text-obsidian transition-all ${INTERACTIVE_TIGHT}`}
                          >
                            Join
                          </button>
                        )}
                      </div>
                    </div>
                    {cdActive && (
                      <div
                        role="status"
                        aria-live="polite"
                        className="mt-2 mb-1 flex items-center justify-between gap-3 px-2 py-1.5 rounded border border-breach/30 bg-breach/5 font-mono text-[10px] uppercase tracking-widest text-breach"
                      >
                        <span>Dispatch cooling · retry in {cdRemainingSec}s</span>
                        <span className="text-muted-foreground normal-case tracking-normal">
                          ready at {cdReadyAt}
                        </span>
                      </div>
                    )}
                    {openGroup === g.id && (
                      <ul className="mt-3 pt-3 border-t border-white/5 space-y-1">
                        {ms.map((m) => (
                          <li key={m.profile_id} className="flex justify-between text-xs font-mono">
                            <span>{m.profiles?.display_name ?? "—"}</span>
                            <span className="text-muted-foreground">
                              {m.profiles?.lifetime_xp ?? 0} XP
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                );
              })}
              </QueryBoundary>
            </div>
          </div>

          <div className="space-y-10">
            <div>
              <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
                CIRCLE_LEADERBOARD · AVG XP
              </h2>
              <ol className="space-y-2">
                {groupBoard.slice(0, 10).map((g, i) => (
                  <li
                    key={g.group.id}
                    className="flex justify-between text-sm py-2 border-b border-white/5"
                  >
                    <span>
                      <span className="font-mono text-muted-foreground mr-3">
                        {String(i + 1).padStart(2, "0")}
                      </span>
                      {g.group.name}
                    </span>
                    <span className="font-mono text-silver">{g.avg}</span>
                  </li>
                ))}
                {groupBoard.length === 0 && (
                  <li className="font-mono text-xs text-muted-foreground">
                    No circles yet.{" "}
                    <button
                      type="button"
                      onClick={() => document.getElementById("circle-name-input")?.focus()}
                      className={`text-ember underline underline-offset-2 ${INTERACTIVE_TIGHT}`}
                    >
                      Forge one
                    </button>
                  </li>
                )}
              </ol>
            </div>

            <div>
              <h2 className="font-mono text-[10px] tracking-[0.3em] uppercase text-muted-foreground mb-4">
                PERSONAL_LEADERBOARD · LIFETIME XP
              </h2>
              <ol className="space-y-2">
                {personalBoard.map((p, i) => (
                  <li
                    key={p.name + i}
                    className="flex justify-between text-sm py-2 border-b border-white/5"
                  >
                    <span>
                      <span className="font-mono text-muted-foreground mr-3">
                        {String(i + 1).padStart(2, "0")}
                      </span>
                      {p.name}
                    </span>
                    <span className="font-mono text-silver">{p.xp}</span>
                  </li>
                ))}
                {personalBoard.length === 0 && (
                  <li className="font-mono text-xs text-muted-foreground">
                    No XP yet.{" "}
                    <Link
                      to="/start"
                      className={`text-ember underline underline-offset-2 ${INTERACTIVE_TIGHT}`}
                    >
                      Hold a session
                    </Link>
                  </li>
                )}
              </ol>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}
