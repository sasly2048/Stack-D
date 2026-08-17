#!/usr/bin/env node
// Create the four Razorpay subscription Plans that back Stack'd Premium, then
// print the SQL to map each Razorpay plan_id onto our local plans table.
//
// Run ONCE per Razorpay account (test and live are separate accounts, so run
// once for each set of keys). Safe to re-run: it lists existing plans first and
// skips any whose period+interval+amount already match, so you won't create
// duplicates.
//
//   RAZORPAY_KEY_ID=rzp_test_xxx RAZORPAY_KEY_SECRET=yyy node scripts/razorpay-setup-plans.mjs
//
// Then paste the printed UPDATE statements into the Supabase SQL editor (or a
// migration) to fill plans.provider_ref.

const KEY_ID = process.env.RAZORPAY_KEY_ID;
const KEY_SECRET = process.env.RAZORPAY_KEY_SECRET;

if (!KEY_ID || !KEY_SECRET) {
  console.error("Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET in the environment.");
  process.exit(1);
}

const AUTH = "Basic " + Buffer.from(`${KEY_ID}:${KEY_SECRET}`).toString("base64");
const API = "https://api.razorpay.com/v1";

// local plan id -> Razorpay plan definition. amount is in paise (₹1 = 100).
const PLANS = [
  {
    local: "pro_monthly",
    period: "monthly",
    interval: 1,
    amount: 129 * 100,
    name: "Stack'd Pro (Monthly)",
  },
  {
    local: "pro_annual",
    period: "yearly",
    interval: 1,
    amount: 899 * 100,
    name: "Stack'd Pro (Annual)",
  },
  {
    local: "elite_monthly",
    period: "monthly",
    interval: 1,
    amount: 249 * 100,
    name: "Stack'd Elite (Monthly)",
  },
  {
    local: "elite_annual",
    period: "yearly",
    interval: 1,
    amount: 1799 * 100,
    name: "Stack'd Elite (Annual)",
  },
];

async function rzp(path, opts = {}) {
  const res = await fetch(`${API}${path}`, {
    ...opts,
    headers: { Authorization: AUTH, "Content-Type": "application/json", ...(opts.headers ?? {}) },
  });
  const body = await res.json();
  if (!res.ok) {
    throw new Error(`Razorpay ${path} -> ${res.status}: ${JSON.stringify(body)}`);
  }
  return body;
}

async function listExistingPlans() {
  // Razorpay paginates; 100 is plenty for four plans on a fresh account.
  const { items = [] } = await rzp("/plans?count=100");
  return items;
}

function matches(existing, want) {
  return (
    existing.period === want.period &&
    Number(existing.interval) === want.interval &&
    Number(existing.item?.amount) === want.amount &&
    existing.item?.currency === "INR"
  );
}

async function main() {
  const existing = await listExistingPlans();
  const results = [];

  for (const want of PLANS) {
    const found = existing.find((e) => matches(e, want));
    if (found) {
      console.log(`= ${want.local}: reusing existing ${found.id}`);
      results.push({ local: want.local, id: found.id });
      continue;
    }
    const created = await rzp("/plans", {
      method: "POST",
      body: JSON.stringify({
        period: want.period,
        interval: want.interval,
        item: { name: want.name, amount: want.amount, currency: "INR" },
      }),
    });
    console.log(`+ ${want.local}: created ${created.id}`);
    results.push({ local: want.local, id: created.id });
  }

  console.log("\n-- Paste into Supabase SQL editor (or a migration):");
  for (const r of results) {
    console.log(`UPDATE public.plans SET provider_ref = '${r.id}' WHERE id = '${r.local}';`);
  }
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
