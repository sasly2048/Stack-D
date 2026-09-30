import { fileURLToPath } from "node:url";

import { defineConfig } from "vitest/config";

// Separate config for RLS/IDOR integration tests: they hit a local
// `supabase start` stack over real HTTP, so they need the node environment and
// a longer timeout. Kept out of the default `npm test` run.
export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "node",
    include: ["tests/rls/**/*.test.ts"],
    globals: false,
    testTimeout: 30_000,
    hookTimeout: 30_000,
    fileParallelism: false, // shared DB — run serially
  },
});
