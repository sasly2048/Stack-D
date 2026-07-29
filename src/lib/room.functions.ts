import { createServerFn } from "@tanstack/react-start";
import { requireSupabaseAuth } from "@/integrations/supabase/auth-middleware";
import {
  ROOM_CODE_PATTERN,
  getPositive,
  normalizeCode,
  setPositive,
  type ValidateErrorCode,
} from "@/lib/room-code";
import { getIp } from "@/lib/room-code.server";

export type { ValidateErrorCode };

export type ValidateResult =
  | { ok: true; code: string; status: "lobby" | "active"; cached?: boolean }
  | { ok: false; code: ValidateErrorCode; message: string; retryAfter?: number };

const VALIDATE_WINDOW_SEC = 10;
const VALIDATE_MAX_HITS = 6;

export const validateRoomCode = createServerFn({ method: "POST" })
  .inputValidator((data: { code: string }) => ({
    code: normalizeCode(data?.code),
  }))

  .handler(async ({ data }): Promise<ValidateResult> => {
    const code = data.code;

    if (!ROOM_CODE_PATTERN.test(code)) {
      return {
        ok: false,
        code: "invalid_format",
        message: "Code must be exactly 6 letters or digits.",
      };
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
  .inputValidator(
    (data: {
      page?: number;
      pageSize?: number;
      status?: "any" | "active" | "lobby" | "complete" | "aborted";
    }) => ({
      page: Math.max(0, Math.floor(Number(data?.page ?? 0))),
      pageSize: Math.min(MAX_PAGE_SIZE, Math.max(1, Math.floor(Number(data?.pageSize ?? 20)))),
      status: (data?.status ?? "any") as "any" | "active" | "lobby" | "complete" | "aborted",
    }),
  )
  .middleware([requireSupabaseAuth])
  .handler(async ({ data, context }): Promise<RoomListResult> => {
    const { supabase, userId } = context;
    const from = data.page * data.pageSize;
    const to = from + data.pageSize; // over-fetch by 1 to compute hasMore

    // Two paths: rooms I host, rooms I participate in. RLS already scopes.
    let q = supabase
      .from("rooms")
      .select(
        "id, code, status, target_duration_seconds, started_at, ended_at, created_at, host_id",
      )
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
