import { describe, expect, it } from "vitest";
import { parseModelJson } from "@/lib/ai.server";

describe("parseModelJson", () => {
  it("parses bare JSON", () => {
    expect(parseModelJson<{ a: number }>('{"a":1}')).toEqual({ a: 1 });
  });

  it("unwraps a ```json fence (Gemini sometimes adds one)", () => {
    expect(parseModelJson<{ a: number }>('```json\n{"a":1}\n```')).toEqual({ a: 1 });
    expect(parseModelJson<{ a: number }>('```\n{"a":1}\n```')).toEqual({ a: 1 });
  });
});
