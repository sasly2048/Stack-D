import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";

export interface Partner {
  relationship_id: string;
  partner_id: string;
  role: "mentor" | "mentee";
  display_name: string | null;
  avatar_url: string | null;
  status: string;
  /** True when the other party sent the invite and it awaits this user. */
  incoming: boolean;
  created_at: string;
}

export const listPartners = createServerFn({ method: "GET" })
  .middleware([requireSupabaseAuth])
  .handler(async ({ context }): Promise<{ rows: Partner[] }> => {
    const { data: rels } = await context.supabase
      .from("mentor_relationships")
      .select("id,mentor_id,mentee_id,status,created_at,initiator_id")
      .or(`mentor_id.eq.${context.userId},mentee_id.eq.${context.userId}`);
    const rows = (rels ?? []) as Array<{
      id: string;
      mentor_id: string;
      mentee_id: string;
      status: string;
      created_at: string;
      initiator_id: string | null;
    }>;
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
          incoming: r.status === "pending" && r.initiator_id !== context.userId,
          created_at: r.created_at,
        };
      }),
    };
  });

/**
 * Sends a pairing *invitation*. The row is created as `pending` and the
 * counterparty must accept before it becomes active — RLS enforces both the
 * pending-on-insert rule and that only the invitee can activate it.
 */
export const pairPartner = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { partnerId: string; asRole: "mentor" | "mentee" }) => input)
  .handler(async ({ data, context }): Promise<{ id: string }> => {
    if (data.partnerId === context.userId) throw new Error("self");
    const mentor = data.asRole === "mentor" ? context.userId : data.partnerId;
    const mentee = data.asRole === "mentor" ? data.partnerId : context.userId;
    const { data: row, error } = await context.supabase
      .from("mentor_relationships")
      .upsert(
        {
          mentor_id: mentor,
          mentee_id: mentee,
          status: "pending",
          initiator_id: context.userId,
        } as never,
        { onConflict: "mentor_id,mentee_id" },
      )
      .select("id")
      .single();
    if (error) throw new Error(error.message);
    return { id: row!.id };
  });

/** Only the invited party can accept or decline. */
export const respondToPairing = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { relationshipId: string; accept: boolean }) => input)
  .handler(async ({ data, context }): Promise<{ ok: true }> => {
    const { error } = await context.supabase
      .from("mentor_relationships")
      .update({ status: data.accept ? "active" : "declined" })
      .eq("id", data.relationshipId);
    if (error) throw new Error(error.message);
    return { ok: true };
  });

export const endPartnership = createServerFn({ method: "POST" })
  .middleware([requireSupabaseAuth])
  .inputValidator((input: { relationshipId: string }) => input)
  .handler(async ({ data, context }): Promise<{ ok: true }> => {
    await context.supabase.from("mentor_relationships").delete().eq("id", data.relationshipId);
    return { ok: true };
  });
