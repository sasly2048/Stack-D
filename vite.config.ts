// Plain Vite config for TanStack Start, built for Cloudflare Workers.
// Supabase URL/keys come from VITE_* env vars (.env locally, Cloudflare build
// settings in CI); there is deliberately no hard-coded fallback project.
import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";
import tsConfigPaths from "vite-tsconfig-paths";
import { tanstackStart } from "@tanstack/react-start/plugin/vite";
import { nitro } from "nitro/vite";
import viteReact from "@vitejs/plugin-react";

export default defineConfig({
  css: { transformer: "lightningcss" },
  resolve: {
    dedupe: [
      "react",
      "react-dom",
      "react/jsx-runtime",
      "react/jsx-dev-runtime",
      "@tanstack/react-query",
      "@tanstack/query-core",
    ],
  },
  plugins: [
    tailwindcss(),
    tsConfigPaths({ projects: ["./tsconfig.json"] }),
    tanstackStart({
      server: { entry: "server" },
      // Server-only code must never reach the browser bundle.
      importProtection: {
        behavior: "error",
        client: { files: ["**/server/**"], specifiers: ["server-only"] },
      },
    }),
    nitro({
      preset: "cloudflare-module",
      // Emits the wrangler config next to the build so `wrangler deploy` needs no hand-written file.
      cloudflare: {
        nodeCompat: true,
        deployConfig: true,
        wrangler: {
          name: "stackd",
          // Served on our own domain; no *.workers.dev address. beta stays as
          // a second hostname for testing.
          workers_dev: false,
          routes: [
            { pattern: "stackd.raghav.studio", custom_domain: true },
            { pattern: "beta.raghav.studio", custom_domain: true },
          ],
          // Public (non-secret) server config; secrets go in via `wrangler secret put`.
          vars: {
            SUPABASE_URL: "https://grsekkegkpwwgzrqvlqk.supabase.co",
            SUPABASE_PUBLISHABLE_KEY: "sb_publishable_rqLKiMFbGUkEFmHLqb8sjg_abzll6QH",
            SUPABASE_PROJECT_ID: "grsekkegkpwwgzrqvlqk",
          },
        },
      },
    }),
    viteReact(),
  ],
});
