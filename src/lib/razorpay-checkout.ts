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
  onDismiss?: () => void;
}): Promise<void> {
  await loadScript();
  if (!window.Razorpay) throw new Error("Razorpay unavailable");

  const rzp = new window.Razorpay({
    key: opts.keyId,
    subscription_id: opts.subscriptionId,
    name: opts.name ?? "Stack'd Premium",
    description: opts.description,
    theme: { color: "#f0a968" }, // ember, matches the product
    modal: { ondismiss: opts.onDismiss },
  });
  rzp.open();
}
