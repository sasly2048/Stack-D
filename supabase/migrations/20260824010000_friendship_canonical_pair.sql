-- =========================================================
-- P1 #15: friendships — one row per unordered pair (canonical constraint)
-- =========================================================
-- The table stores a friendship directionally (requester_id -> addressee_id)
-- and its only uniqueness guard is UNIQUE (requester_id, addressee_id). So
-- A->B and B->A are two distinct rows: both users can send each other a pending
-- request, producing a duplicate friendship that can settle into a split-brain
-- state (one row 'accepted', the reciprocal still 'pending'). are_friends()
-- already has to check both orderings, which is the tell that a pair is meant
-- to be canonical but nothing enforces it.
--
-- Fix: a unique index on the *unordered* pair
-- (LEAST(requester_id,addressee_id), GREATEST(...)) so a reciprocal request is
-- rejected at the database. Existing reciprocal duplicates must be removed
-- first or the index build fails — keep the most-progressed row per pair
-- (accepted beats pending/blocked), tie-broken by oldest created_at.
--
-- The directional columns stay: who requested whom still drives the accept
-- flow (friendship_guard lets only the addressee accept). Only *uniqueness*
-- becomes canonical.
--
-- Idempotent: dedupe is a plain DELETE; the index uses IF NOT EXISTS.

-- 1) Remove the reciprocal loser of any duplicated pair. Rank rows within each
--    unordered pair: accepted first, then oldest. Anything past rank 1 goes.
WITH ranked AS (
  SELECT
    id,
    row_number() OVER (
      PARTITION BY LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id)
      ORDER BY
        (status = 'accepted') DESC,   -- keep an accepted row over a pending one
        created_at ASC,               -- else keep the earliest
        id ASC                        -- final deterministic tiebreak
    ) AS rn
  FROM public.friendships
)
DELETE FROM public.friendships f
USING ranked r
WHERE f.id = r.id AND r.rn > 1;

-- 2) Canonical uniqueness: at most one row per unordered pair.
CREATE UNIQUE INDEX IF NOT EXISTS friendships_canonical_pair_idx
  ON public.friendships (
    LEAST(requester_id, addressee_id),
    GREATEST(requester_id, addressee_id)
  );
