/**
 * Server-only helper for the Gemini API's OpenAI-compatible chat completions
 * endpoint (free tier). Read process.env.GEMINI_API_KEY inside handler bodies,
 * never at module scope.
 */

const GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
/** Alias that tracks Google's current Flash model; override with GEMINI_MODEL. */
const DEFAULT_MODEL = "gemini-flash-latest";

export type ChatMessage = { role: "system" | "user" | "assistant"; content: string };

/** Gemini sometimes wraps JSON in a ```json fence even when asked for an object. */
export function parseModelJson<T>(content: string): T {
  const fenced = content.trim().match(/^```(?:json)?\s*([\s\S]*?)\s*```$/);
  return JSON.parse(fenced ? fenced[1] : content) as T;
}

async function complete(messages: ChatMessage[], extra: Record<string, unknown>): Promise<string> {
  const key = process.env.GEMINI_API_KEY;
  if (!key) throw new Error("GEMINI_API_KEY missing");

  const res = await fetch(GEMINI_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${key}`,
    },
    body: JSON.stringify({ model: process.env.GEMINI_MODEL || DEFAULT_MODEL, messages, ...extra }),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    const err = new Error(`AI ${res.status}: ${text.slice(0, 300)}`);
    (err as Error & { status?: number }).status = res.status;
    throw err;
  }

  const json = (await res.json()) as {
    choices?: Array<{ message?: { content?: string } }>;
  };
  const content = json.choices?.[0]?.message?.content;
  if (!content) throw new Error("AI returned no content");
  return content;
}

export async function callAIJson<T = unknown>(opts: {
  messages: ChatMessage[];
  /** Ignored: kept so existing callers compile; GEMINI_MODEL picks the model. */
  model?: string;
  temperature?: number;
}): Promise<T> {
  const content = await complete(opts.messages, {
    temperature: opts.temperature ?? 0.9,
    response_format: { type: "json_object" },
  });
  return parseModelJson<T>(content);
}

/** Plain-text completion: a system prompt and one user message. */
export async function callAIText(system: string, prompt: string): Promise<string> {
  const content = await complete(
    [
      { role: "system", content: system },
      { role: "user", content: prompt },
    ],
    {},
  );
  return content.trim();
}

/** Brand tone shared by all Stack'd AI prompts. */
export const BRAND_TONE = `You write for Stack'd — a private protocol for shared, intentional offline focus sessions.
Voice: obsidian, ceremonial, restrained, technical. Think Dieter Rams meets a monastery meets a spec sheet.
Rules: Short sentences. No hype adjectives ("amazing", "incredible", "revolutionary"). No emoji. No exclamation marks.
Prefer nouns: presence, protocol, signal, stillness, session, room, silence. Avoid: "digital detox", "productivity hacks", "unplug".`;
