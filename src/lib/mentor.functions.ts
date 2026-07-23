import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export interface Partner {
  relationship_id: string;
  partner_id: string;
  role: "mentor" | "mentee";
  display_name: string | null;
  avatar_url: string | null;
  status: string;
  created_at: string;
}

export const listPartners = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ rows: Partner[] }> => {
    const { data: rels } = await context.supabase
      .from("mentor_relationships")
      .select("id,mentor_id,mentee_id,status,created_at")
      .or(`mentor_id.eq.${context.userId},mentee_id.eq.${context.userId}`);
    const rows = rels ?? [];
    if (rows.length === 0) return { rows: [] };
    const partnerIds = Array.from(
      new Set(rows.map((r) => (r.mentor_id === context.userId ? r.mentee_id : r.mentor_id))),
    );
    const { data: profs } = await context.supabase
      .from("profiles")
      .select("id,display_name,avatar_url")
      .in("id", partnerIds);
    const pmap = new Map((profs ?? []).map((p) => [p.id, p]));
    return {
      rows: rows.map((r) => {
        const isMentor = r.mentor_id === context.userId;
        const pid = isMentor ? r.mentee_id : r.mentor_id;
        const p = pmap.get(pid);
        return {
          relationship_id: r.id,
          partner_id: pid,
          role: isMentor ? "mentor" : "mentee",
          display_name: p?.display_name ?? null,
          avatar_url: p?.avatar_url ?? null,
          status: r.status,
          created_at: r.created_at,
        };
      }),
    };
  });

export const pairPartner = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { partnerId: string; asRole: "mentor" | "mentee" }) => input)
  .handler(async ({ data, context }): Promise<{ id: string }> => {
    if (data.partnerId === context.userId) throw new Error("self");
    const mentor = data.asRole === "mentor" ? context.userId : data.partnerId;
    const mentee = data.asRole === "mentor" ? data.partnerId : context.userId;
    const { data: row, error } = await context.supabase
      .from("mentor_relationships")
      .upsert({ mentor_id: mentor, mentee_id: mentee, status: "active" }, { onConflict: "mentor_id,mentee_id" })
      .select("id")
      .single();
    if (error) throw new Error(error.message);
    return { id: row!.id };
  });

export const endPartnership = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { relationshipId: string }) => input)
  .handler(async ({ data, context }): Promise<{ ok: true }> => {
    await context.supabase.from("mentor_relationships").delete().eq("id", data.relationshipId);
    return { ok: true };
  });
