-- Captures the `plans` and `subscriptions` tables (and the `access_tier` enum
-- they depend on) that were created directly in the Lovable/Supabase dashboard
-- and never committed as a migration. Without this file a fresh `supabase db
-- push` to a standalone project would be missing the premium/entitlement
-- backbone: PremiumRepository.myEntitlement() reads `subscriptions`, and the
-- upgrade screens read `plans`.
--
-- Reconstructed from the live schema (information_schema / pg_catalog dumps) on
-- 2026-09-19. Idempotent: safe to run against the current project (where these
-- objects already exist) and against a fresh one.

-- access_tier: free < pro < elite. `elite` is a superset of `pro` in the app
-- (Entitlement.isPro is true for both). Referenced by plans.tier and
-- subscriptions.tier, and by the SECURITY DEFINER entitlement RPCs.
do $$
begin
  if not exists (select 1 from pg_type where typname = 'access_tier') then
    create type public.access_tier as enum ('free', 'pro', 'elite');
  end if;
end $$;

-- plans: the purchasable tiers surfaced on the upgrade screen. Publicly
-- readable when active; never written from the client (seeded here / by admin).
create table if not exists public.plans (
  id           text primary key,
  tier         public.access_tier not null,
  interval     text not null check (interval = any (array['monthly'::text, 'annual'::text])),
  price_inr    integer not null check (price_inr >= 0),
  display_name text not null,
  is_active    boolean not null default true,
  sort_order   integer not null default 0,
  created_at   timestamptz not null default now(),
  -- Payment-provider plan reference (e.g. Razorpay plan id). Nullable: a plan
  -- can exist before it's wired to a provider.
  provider_ref text
);

alter table public.plans enable row level security;

-- Anyone (even signed-out) may read active plans — the pricing page is public.
drop policy if exists "Anyone can read active plans" on public.plans;
create policy "Anyone can read active plans"
  on public.plans for select
  to public
  using (is_active);

-- subscriptions: one row per user, their current entitlement. `tier` is the
-- effective access level the app gates on; `source` records how it was granted.
create table if not exists public.subscriptions (
  user_id            uuid primary key references auth.users(id) on delete cascade,
  tier               public.access_tier not null default 'free',
  source             text not null default 'none'
                       check (source = any (array['none'::text, 'razorpay'::text, 'lifetime'::text, 'manual'::text])),
  current_period_end timestamptz,
  provider_ref       text,
  plan_id            text references public.plans(id),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

alter table public.subscriptions enable row level security;

-- A user may read only their own subscription. Writes go through SECURITY
-- DEFINER RPCs (grant/upgrade), never directly from the client — so there is
-- deliberately no INSERT/UPDATE policy here.
drop policy if exists "Users read own subscription" on public.subscriptions;
create policy "Users read own subscription"
  on public.subscriptions for select
  to authenticated
  using (auth.uid() = user_id);

-- Seed the current plan catalogue (prices in INR paise-free rupees, as stored).
insert into public.plans (id, tier, interval, price_inr, display_name, is_active, sort_order, provider_ref, created_at) values
  ('pro_monthly',   'pro',   'monthly', 129,  'Pro Monthly',   true, 1, 'plan_TR9abCG3URLzO0', '2026-08-18 01:07:19.036067+00'),
  ('pro_annual',    'pro',   'annual',  899,  'Pro Annual',    true, 2, 'plan_TR9cI3LPxdTbkN', '2026-08-18 01:07:19.036067+00'),
  ('elite_monthly', 'elite', 'monthly', 249,  'Elite Monthly', true, 3, 'plan_TR9uw5jiMyCoFH', '2026-08-18 01:07:19.036067+00'),
  ('elite_annual',  'elite', 'annual',  1799, 'Elite Annual',  true, 4, 'plan_TR9ccmwtimXi7L', '2026-08-18 01:07:19.036067+00')
on conflict (id) do nothing;
