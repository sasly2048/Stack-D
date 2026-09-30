/**
 * Loads Razorpay Checkout on demand and opens it for a subscription. Client-only.
 * The Key ID is public by design (it ships in this script); the secret never
 * touches the client — the subscription is created server-side first.
 */

const SCRIPT_SRC = "https://checkout.razorpay.com/v1/checkout.js";

interface RazorpayCtor {
  new (opts: Record<string, unknown>): { open: () => void };
}

declare global {
  interface Window {
    Razorpay?: RazorpayCtor;
  }
}

let scriptPromise: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if (typeof window === "undefined") return Promise.reject(new Error("no window"));
  if (window.Razorpay) return Promise.resolve();
  scriptPromise ??= new Promise<void>((resolve, reject) => {
    const el = document.createElement("script");
    el.src = SCRIPT_SRC;
    el.async = true;
    el.onload = () => resolve();
    el.onerror = () => {
      scriptPromise = null; // allow retry on a later attempt
      reject(new Error("Failed to load Razorpay Checkout"));
    };
    document.head.appendChild(el);
  });
  return scriptPromise;
}

/**
 * Open Razorpay Checkout for a created subscription. Resolves when the modal is
 * launched; payment success is confirmed server-side by the webhook, not here —
 * the caller should refresh entitlement on dismiss rather than trust the client.
 */
export async function openRazorpayCheckout(opts: {
  keyId: string;
  subscriptionId: string;
  name?: string;
  description?: string;
  /** Fires when the user completes payment (before the webhook confirms). */
  onSuccess?: () => void;
  /** Fires when the user closes/cancels the modal. */
  onDismiss?: () => void;
}): Promise<void> {
  await loadScript();
  if (!window.Razorpay) throw new Error("Razorpay unavailable");

  // Safety net: Radix Dialog (our upgrade modal) can briefly leave
  // `pointer-events: none` on <body> while it unmounts. If that lands right as
  // Razorpay opens, the whole page — Razorpay's iframe included — becomes
  // unclickable. Clear it on the next frame so control is fully handed over.
  requestAnimationFrame(() => {
    if (document.body.style.pointerEvents === "none") {
      document.body.style.pointerEvents = "";
    }
  });

  const rzp = new window.Razorpay({
    key: opts.keyId,
    subscription_id: opts.subscriptionId,
    name: opts.name ?? "Stack'd Premium",
    description: opts.description,
    theme: { color: "#f0a968" }, // ember, matches the product
    // handler fires on successful payment. Authoritative confirmation is still
    // the webhook writing the subscription row — the caller polls entitlement
    // from here rather than trusting this client signal outright.
    handler: () => opts.onSuccess?.(),
    modal: { ondismiss: opts.onDismiss },
  });
  rzp.open();
}
