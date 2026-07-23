import { createServerFn } from "@tanstack/react-start";
import { getRequestIP } from "@tanstack/react-start/server";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";


export type ValidateErrorCode =
  | "invalid_format"
  | "not_found"
  | "closed"
  | "rate_limited"
  | "server_error";

export type ValidateResult =
  | { ok: true; code: string; status: "lobby" | "active"; cached?: boolean }
  | { ok: false; code: ValidateErrorCode; message: string; retryAfter?: number };

const VALIDATE_WINDOW_SEC = 10;
const VALIDATE_MAX_HITS = 6;

/** Short-lived per-Worker positive cache: skip DB round-trips for codes that
 *  just resolved. Negative results are intentionally NOT cached so a room
 *  becoming active is picked up on the next attempt. */
type Positive = { code: string; status: "lobby" | "active"; expires: number };
const positiveCache = new Map<string, Positive>();
const POSITIVE_TTL_MS = 30_000; // 30s

function getPositive(code: string): Positive | null {
  const hit = positiveCache.get(code);
  if (!hit) return null;
  if (Date.now() > hit.expires) {
    positiveCache.delete(code);
    return null;
  }
  return hit;
}
function setPositive(code: string, status: "lobby" | "active") {
  positiveCache.set(code, { code, status, expires: Date.now() + POSITIVE_TTL_MS });
  // Bound the cache — this Worker never needs more than a small ring.
  if (positiveCache.size > 500) {
    const oldest = positiveCache.keys().next().value;
    if (oldest) positiveCache.delete(oldest);
  }
}

function normalizeCode(raw: unknown): string {
  if (typeof raw !== "string") return "";
  return raw.normalize("NFKC").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6);
}

function getIp(): string {
  try {
    return getRequestIP({ xForwardedFor: true }) ?? "unknown";
  } catch {
    return "unknown";
  }
}

export const validateRoomCode = createServerFn({ method: "POST" })
  .inputValidator((data: { code: string }) => ({
    code: normalizeCode(data?.code),
  }))
  .handler(async ({ data }): Promise<ValidateResult> => {
    const code = data.code;

    if (!/^[A-Z0-9]{6}$/.test(code)) {
      return { ok: false, code: "invalid_format", message: "Code must be exactly 6 letters or digits." };
    }

    // Fast path — recent positive resolution. Skips DB + rate limit for repeat
    // checks on the same key (e.g. the user hitting Join twice, or a re-render
    // triggered validation). Negative outcomes never hit this path.
    const cached = getPositive(code);
    if (cached) {
      return { ok: true, code: cached.code, status: cached.status, cached: true };
    }

    const { supabaseAdmin } = await import("@/integrations/supabase/client.server");

    const ip = getIp();
    const { data: limited, error: rlErr } = await supabaseAdmin.rpc("check_and_record_hit", {
      _key: `validate_room:${ip}`,
      _window_seconds: VALIDATE_WINDOW_SEC,
      _max_hits: VALIDATE_MAX_HITS,
    });
    if (rlErr) {
      console.error("rate_limit_error", rlErr);
    } else if (limited) {
      return {
        ok: false,
        code: "rate_limited",
        message: `Too many attempts. Wait ${VALIDATE_WINDOW_SEC}s and retry.`,
        retryAfter: VALIDATE_WINDOW_SEC,
      };
    }

    const { data: room, error } = await supabaseAdmin
      .from("rooms")
      .select("code,status")
      .eq("code", code)
      .maybeSingle();

    if (error) {
      console.error("validate_room_db_error", error);
      return { ok: false, code: "server_error", message: "Couldn't reach the protocol. Retry." };
    }
    if (!room) {
      return { ok: false, code: "not_found", message: "No room with that key." };
    }
    if (room.status === "complete" || room.status === "aborted") {
      return { ok: false, code: "closed", message: "That session has already ended." };
    }
    const status = room.status as "lobby" | "active";
    setPositive(room.code, status);
    return { ok: true, code: room.code, status };
  });

/* -------------------------------------------------------------------------- */
/*  Paginated room discovery — dashboard/history views, capped page size.     */
/* -------------------------------------------------------------------------- */

export type RoomListItem = {
  id: string;
  code: string;
  status: "lobby" | "active" | "complete" | "aborted";
  target_duration_seconds: number;
  started_at: string | null;
  ended_at: string | null;
  created_at: string;
  is_host: boolean;
};

export type RoomListResult = {
  items: RoomListItem[];
  page: number;
  pageSize: number;
  hasMore: boolean;
};

const MAX_PAGE_SIZE = 50;

/** Owner-scoped paginated list of the caller's rooms — participant OR host.
 *  Uses RLS via `requireSupabaseAuth` so the query itself enforces ownership,
 *  and caps page size to keep bandwidth predictable for large user counts. */
export const listMyRooms = createServerFn({ method: "POST" })
  .inputValidator((data: { page?: number; pageSize?: number; status?: "any" | "active" | "lobby" | "complete" | "aborted" }) => ({
    page: Math.max(0, Math.floor(Number(data?.page ?? 0))),
    pageSize: Math.min(MAX_PAGE_SIZE, Math.max(1, Math.floor(Number(data?.pageSize ?? 20)))),
    status: (data?.status ?? "any") as "any" | "active" | "lobby" | "complete" | "aborted",
  }))
  .middleware([requireSupabaseAuth])
  .handler(async ({ data, context }): Promise<RoomListResult> => {
    const { supabase, userId } = context;
    const from = data.page * data.pageSize;
    const to = from + data.pageSize; // over-fetch by 1 to compute hasMore

    // Two paths: rooms I host, rooms I participate in. RLS already scopes.
    let q = supabase
      .from("rooms")
      .select("id, code, status, target_duration_seconds, started_at, ended_at, created_at, host_id")
      .order("created_at", { ascending: false })
      .range(from, to);
    if (data.status !== "any") q = q.eq("status", data.status);

    const { data: rows, error } = await q;
    if (error) {
      console.error("list_my_rooms_error", error);
      return { items: [], page: data.page, pageSize: data.pageSize, hasMore: false };
    }

    const slice = (rows ?? []).slice(0, data.pageSize);
    const items: RoomListItem[] = slice.map((r) => ({
      id: r.id,
      code: r.code,
      status: r.status as RoomListItem["status"],
      target_duration_seconds: r.target_duration_seconds,
      started_at: r.started_at,
      ended_at: r.ended_at,
      created_at: r.created_at,
      is_host: r.host_id === userId,
    }));

    return {
      items,
      page: data.page,
      pageSize: data.pageSize,
      hasMore: (rows?.length ?? 0) > data.pageSize,
    };
  });
