#!/usr/bin/env node
// Bundle budget gate.
//
//   bun run build && bun run analyze
//
// Measures gzipped size of the built client assets and fails when a budget is
// exceeded. Budgets are deliberately a little above current size so normal
// feature work passes, but a careless heavy import trips the gate.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { gzipSync } from "node:zlib";
import { join, extname, basename } from "node:path";

const DIST = "dist/client/assets";

/** Gzipped budgets in KiB. */
const BUDGETS = {
  // Everything the browser must download for first paint of "/".
  "initial-js": 240,
  "initial-css": 26,
  // Any single lazily-loaded route/library chunk.
  "largest-lazy-chunk": 140,
  // Whole client build.
  "total-js": 760,
};

const KIB = 1024;
const gz = (p) => gzipSync(readFileSync(p)).length / KIB;

function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    if (statSync(p).isDirectory()) out.push(...walk(p));
    else out.push(p);
  }
  return out;
}

let files;
try {
  files = walk(DIST);
} catch {
  console.error(`No build found at ${DIST}. Run \`bun run build\` first.`);
  process.exit(1);
}

const js = files.filter((f) => extname(f) === ".js");
const css = files.filter((f) => extname(f) === ".css");

// The entry chunk is the one the HTML boots from; Vite names it `index-*.js`.
const entry = js.find((f) => basename(f).startsWith("index-")) ?? js[0];
const lazy = js.filter((f) => f !== entry);

const measured = js.map((f) => ({ file: basename(f), kib: gz(f) })).sort((a, b) => b.kib - a.kib);

const totals = {
  "initial-js": gz(entry),
  "initial-css": css.reduce((s, f) => s + gz(f), 0),
  "largest-lazy-chunk": lazy.length ? Math.max(...lazy.map(gz)) : 0,
  "total-js": js.reduce((s, f) => s + gz(f), 0),
};

console.log("\nTop client chunks (gzipped)\n" + "-".repeat(52));
for (const { file, kib } of measured.slice(0, 12)) {
  console.log(`${kib.toFixed(1).padStart(8)} KiB  ${file}`);
}

console.log("\nBudgets (gzipped)\n" + "-".repeat(52));
let failed = 0;
for (const [name, budget] of Object.entries(BUDGETS)) {
  const actual = totals[name];
  const ok = actual <= budget;
  if (!ok) failed++;
  const pct = ((actual / budget) * 100).toFixed(0);
  console.log(
    `${ok ? "OK  " : "OVER"}  ${name.padEnd(20)} ${actual.toFixed(1).padStart(7)} / ${String(budget).padStart(4)} KiB  (${pct}%)`,
  );
}

console.log("");
if (failed) {
  console.error(
    `${failed} budget(s) exceeded. Lazy-load the offending dependency or raise the budget deliberately in scripts/bundle-budget.mjs.`,
  );
  process.exit(1);
}
console.log("All bundle budgets met.");
