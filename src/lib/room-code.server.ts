import { getRequestIP } from "@tanstack/react-start/server";

/** Caller IP for rate-limit keys; never throws into the handler. */
export function getIp(): string {
  try {
    return getRequestIP({ xForwardedFor: true }) ?? "unknown";
  } catch {
    return "unknown";
  }
}
