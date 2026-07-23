/**
 * Server-only helper for Lovable AI Gateway (OpenAI-compatible /v1/chat/completions).
 * Read process.env.LOVABLE_API_KEY inside handler bodies, never at module scope.
 */

const GATEWAY_URL = "https://ai.gateway.lovable.dev/v1/chat/completions";

export type ChatMessage = { role: "system" | "user" | "assistant"; content: string };

export async function callAIJson<T = unknown>(opts: {
  messages: ChatMessage[];
  model?: string;
  temperature?: number;
}): Promise<T> {
  const key = process.env.LOVABLE_API_KEY;
  if (!key) throw new Error("LOVABLE_API_KEY missing");

  const res = await fetch(GATEWAY_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Lovable-API-Key": key,
    },
    body: JSON.stringify({
      model: opts.model ?? "openai/gpt-5.5",
      messages: opts.messages,
      temperature: opts.temperature ?? 0.9,
      response_format: { type: "json_object" },
    }),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    const err = new Error(`AI gateway ${res.status}: ${text.slice(0, 300)}`);
    (err as Error & { status?: number }).status = res.status;
    throw err;
  }

  const json = (await res.json()) as {
    choices?: Array<{ message?: { content?: string } }>;
  };
  const content = json.choices?.[0]?.message?.content;
  if (!content) throw new Error("AI gateway returned no content");
  return JSON.parse(content) as T;
}

/** Brand tone shared by all Stack'd AI prompts. */
export const BRAND_TONE = `You write for Stack'd — a private protocol for shared, intentional offline focus sessions.
Voice: obsidian, ceremonial, restrained, technical. Think Dieter Rams meets a monastery meets a spec sheet.
Rules: Short sentences. No hype adjectives ("amazing", "incredible", "revolutionary"). No emoji. No exclamation marks.
Prefer nouns: presence, protocol, signal, stillness, session, room, silence. Avoid: "digital detox", "productivity hacks", "unplug".`;
