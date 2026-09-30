import { describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { aiErrorResponse } from "@/lib/ai-public-auth";

async function body(res: Response) {
  return { status: res.status, json: await res.json() };
}

describe("aiErrorResponse", () => {
  it("maps a gateway 402 (out of credits) to 503 ai_unavailable/credits", async () => {
    const err = Object.assign(new Error("AI gateway 402: Not enough credits"), { status: 402 });
    expect(await body(aiErrorResponse(err))).toEqual({
      status: 503,
      json: { error: "ai_unavailable", reason: "credits" },
    });
  });

  it("maps a gateway 429 to rate_limited and other gateway codes to gateway", async () => {
    const r429 = Object.assign(new Error("x"), { status: 429 });
    const r500 = Object.assign(new Error("x"), { status: 500 });
    expect((await body(aiErrorResponse(r429))).json.reason).toBe("rate_limited");
    expect((await body(aiErrorResponse(r500))).json.reason).toBe("gateway");
  });

  it("maps validation failures and malformed JSON to 400", async () => {
    const zod = z.object({ id: z.string().uuid() }).safeParse({ id: "nope" });
    expect(zod.success).toBe(false);
    if (!zod.success) expect(aiErrorResponse(zod.error).status).toBe(400);
    expect(aiErrorResponse(new SyntaxError("Unexpected token")).status).toBe(400);
  });

  it("maps an exhausted AI allowance to 402 ai_quota with the user-facing message", async () => {
    const msg = "You've used all 20 of your AI actions for this billing period.";
    expect(await body(aiErrorResponse(new Error(msg)))).toEqual({
      status: 402,
      json: { error: "ai_quota", message: msg },
    });
  });

  it("maps anything else to a JSON 500, never an HTML page", async () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    const res = aiErrorResponse(new Error("boom"));
    expect(res.status).toBe(500);
    expect(res.headers.get("content-type")).toContain("application/json");
    expect(await res.json()).toEqual({ error: "internal" });
    spy.mockRestore();
  });
});
