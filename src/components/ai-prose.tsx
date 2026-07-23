import { useEffect, useState } from "react";
import { useServerFn } from "@tanstack/react-start";
import { generateBrandProse, type BrandProse } from "@/lib/ai.functions";

const FALLBACK: BrandProse = {
  landingKicker: "Signal held. Notifications silenced. Room online.",
  landingLede:
    "Every session is a small treaty against the feed — six characters, one room, and the steady proof of stillness.",
  philosophyOpener:
    "The attention economy is not a metaphor. It is a supply chain, and you were the yield.",
  philosophyClosing: "Presence is not restored. It is practiced.",
  generatedAt: new Date(0).toISOString(),
};

/** Fetches AI-written brand prose once per mount. Falls back silently on error. */
export function useBrandProse(): { data: BrandProse; loading: boolean } {
  const fetchProse = useServerFn(generateBrandProse);
  const [data, setData] = useState<BrandProse>(FALLBACK);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    fetchProse()
      .then((res) => {
        if (mounted && res) setData(res);
      })
      .catch(() => {})
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [fetchProse]);

  return { data, loading };
}
