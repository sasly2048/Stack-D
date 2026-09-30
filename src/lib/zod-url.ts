import { z } from "zod";

import { isHttpScheme } from "@/lib/safe-url";

/**
 * A URL that must be http(s). z.string().url() alone accepts javascript:,
 * data:, file:, etc., which are dangerous when later rendered as an href
 * (Codex #33/#34). Use this for any user-supplied link we store and display.
 */
export const httpUrl = z
  .string()
  .url()
  .refine(isHttpScheme, { message: "URL must use http or https" });
