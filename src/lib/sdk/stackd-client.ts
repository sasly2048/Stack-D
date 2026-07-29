/**
 * Stack'd Public SDK — tiny TypeScript client for the /api/public webhook
 * verification and profile-read endpoints. Ships as ESM. Zero deps.
 *
 * Usage:
 *   import { StackdClient } from "@stackd/sdk";
 *   const client = new StackdClient({ baseUrl: "https://stack-d.lovable.app" });
 *   const ok = await client.verifyWebhookSignature(rawBody, header, secret);
 */

export interface StackdClientOptions {
  baseUrl: string;
  apiKey?: string;
}

export interface WebhookEvent<T = unknown> {
  id: string;
  type: string;
  created_at: string;
  data: T;
}

export class StackdClient {
  private baseUrl: string;
  private apiKey?: string;

  constructor(opts: StackdClientOptions) {
    this.baseUrl = opts.baseUrl.replace(/\/$/, "");
    this.apiKey = opts.apiKey;
  }

  /**
   * Verify a webhook payload using HMAC-SHA256.
   * Works in browsers, Node, Deno, and Workers via WebCrypto.
   */
  async verifyWebhookSignature(rawBody: string, header: string, secret: string): Promise<boolean> {
    const enc = new TextEncoder();
    const key = await crypto.subtle.importKey(
      "raw",
      enc.encode(secret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const sig = await crypto.subtle.sign("HMAC", key, enc.encode(rawBody));
    const hex = Array.from(new Uint8Array(sig))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
    return timingSafeEqual(hex, header);
  }

  async parseEvent<T = unknown>(rawBody: string): Promise<WebhookEvent<T>> {
    return JSON.parse(rawBody) as WebhookEvent<T>;
  }

  async health(): Promise<{ ok: boolean; ts: number }> {
    const res = await fetch(`${this.baseUrl}/api/public/health`);
    return res.json();
  }
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let out = 0;
  for (let i = 0; i < a.length; i++) out |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return out === 0;
}
