#!/usr/bin/env node
/**
 * Join button contrast audit.
 *
 * Reads the canonical Join-button CSS variables out of src/styles.css and
 * verifies that every visible state (rest, hover, active, focus ring,
 * loading, disabled) clears WCAG AA contrast against the rendered surface
 * for both the dark and light theme blocks.
 *
 * Run: `node scripts/check-join-contrast.mjs`
 * Exits 1 if any state fails AA (4.5:1 for text, 3:1 for UI/large).
 *
 * Breakpoints don't change colour — only border-width / glow radius — so
 * a single contrast pass per theme covers mobile and high-DPI as well.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const css = readFileSync(resolve(here, "../src/styles.css"), "utf8");

// --- tiny colour helpers ---------------------------------------------------
const parseColor = (raw) => {
  const s = raw.trim();
  const hex = s.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
  if (hex) {
    const h = hex[1].length === 3 ? hex[1].split("").map((c) => c + c).join("") : hex[1];
    return { r: parseInt(h.slice(0, 2), 16), g: parseInt(h.slice(2, 4), 16), b: parseInt(h.slice(4, 6), 16), a: 1 };
  }
  const m = s.match(/^rgba?\(([^)]+)\)$/i);
  if (m) {
    const [r, g, b, a = "1"] = m[1].split(",").map((v) => v.trim());
    return { r: +r, g: +g, b: +b, a: +a };
  }
  throw new Error(`Unparseable colour: ${raw}`);
};
const composite = (fg, bg) => ({
  r: fg.r * fg.a + bg.r * (1 - fg.a),
  g: fg.g * fg.a + bg.g * (1 - fg.a),
  b: fg.b * fg.a + bg.b * (1 - fg.a),
  a: 1,
});
const lum = ({ r, g, b }) => {
  const c = [r, g, b].map((v) => {
    const x = v / 255;
    return x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
};
const ratio = (a, b) => {
  const [l1, l2] = [lum(a), lum(b)].sort((x, y) => y - x);
  return (l1 + 0.05) / (l2 + 0.05);
};

// --- pull variable blocks --------------------------------------------------
const blockOf = (selector) => {
  const re = new RegExp(`${selector.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\$&")}[^{]*\\{([\\s\\S]*?)\\}`);
  const m = css.match(re);
  if (!m) throw new Error(`Could not find block ${selector}`);
  return m[1];
};
const readVars = (block) =>
  Object.fromEntries(
    [...block.matchAll(/--([a-z0-9-]+):\s*([^;]+);/gi)].map(([, k, v]) => [k, v.trim()])
  );

const darkVars = readVars(blockOf(":root"));
const lightVars = { ...darkVars, ...readVars(blockOf(":root.light,\n:root\\[data-theme=\"light\"\\]")) };

// --- audit -----------------------------------------------------------------
const SURFACES = {
  dark:  parseColor("#0A0A0A"), // --color-obsidian
  light: parseColor("#F5F5F5"), // paper surface assumption for light theme
};

// Each state describes: foreground colour + which surface it sits on.
// "fill" states (hover/active) paint --join-fill behind the text, so the
// effective background is the fill composited over the surface.
const states = (v, surface) => {
  const fill = composite(parseColor(v["join-fill"]), surface);
  return [
    { label: "rest text on surface",   fg: v["join-text"],         bg: surface, min: 4.5 },
    { label: "rest border on surface", fg: v["join-border"],       bg: surface, min: 3.0 },
    { label: "hover text on fill",     fg: v["join-text-hover"],   bg: fill,    min: 4.5 },
    { label: "active text on fill",    fg: v["join-text-active"],  bg: fill,    min: 4.5 },
    { label: "focus ring on surface",  fg: v["join-focus-ring"],   bg: surface, min: 3.0 },
    { label: "loading text on surface",fg: v["join-loading-text"], bg: surface, min: 4.5 },
    { label: "loading border",         fg: v["join-loading-border"], bg: surface, min: 3.0 },
    { label: "disabled text",          fg: v["join-disabled-text"], bg: surface, min: 3.0 },
    { label: "disabled border",        fg: v["join-disabled-border"], bg: surface, min: 3.0 },
  ];
};

let failed = 0;
for (const [theme, surface] of Object.entries(SURFACES)) {
  const vars = theme === "dark" ? darkVars : lightVars;
  console.log(`\n▌ ${theme.toUpperCase()} theme — surface ${theme === "dark" ? "#0A0A0A" : "#F5F5F5"}`);
  for (const s of states(vars, surface)) {
    const fg = composite(parseColor(s.fg), s.bg);
    const r = ratio(fg, s.bg);
    const ok = r >= s.min;
    if (!ok) failed++;
    console.log(`  ${ok ? "✓" : "✗"} ${s.label.padEnd(28)} ${r.toFixed(2)}:1  (min ${s.min}:1)`);
  }
}

if (failed) {
  console.error(`\n✗ ${failed} Join contrast check(s) failed.`);
  process.exit(1);
}
console.log("\n✓ All Join button states pass WCAG AA on every theme.");
