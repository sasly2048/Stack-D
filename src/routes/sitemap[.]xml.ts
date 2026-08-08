import { createFileRoute } from "@tanstack/react-router";
import type {} from "@tanstack/react-start";

import { isLabRoute } from "@/lib/feature-flags";
import { SITE_URL } from "@/lib/site";

const BASE_URL = SITE_URL;

export const Route = createFileRoute("/sitemap.xml")({
  server: {
    handlers: {
      GET: async () => {
        // Lab routes are hidden from nav and the command palette, so listing
        // them here would advertise pages no visitor can navigate to. Filtering
        // on the same flag keeps the sitemap honest if a route changes tier.
        const entries = [
          { path: "/", changefreq: "weekly", priority: "1.0" },
          { path: "/philosophy", changefreq: "monthly", priority: "0.8" },
          { path: "/privacy", changefreq: "yearly", priority: "0.3" },
          { path: "/catalog", changefreq: "monthly", priority: "0.6" },
          { path: "/sdk", changefreq: "monthly", priority: "0.6" },
          { path: "/auth", changefreq: "monthly", priority: "0.5" },
        ].filter((e) => !isLabRoute(e.path));
        const urls = entries.map(
          (e) =>
            `  <url>\n    <loc>${BASE_URL}${e.path}</loc>\n    <changefreq>${e.changefreq}</changefreq>\n    <priority>${e.priority}</priority>\n  </url>`,
        );
        const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.join("\n")}\n</urlset>`;
        return new Response(xml, {
          headers: { "Content-Type": "application/xml", "Cache-Control": "public, max-age=3600" },
        });
      },
    },
  },
});
