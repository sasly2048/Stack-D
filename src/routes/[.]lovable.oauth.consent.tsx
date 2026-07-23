import { createFileRoute, redirect } from "@tanstack/react-router";
import { useState } from "react";
import { supabase } from "@/integrations/supabase/client";

// The Supabase OAuth 2.1 authorization server redirects the user here to
// approve or deny a client (e.g. ChatGPT, Claude) requesting access to
// Stack'd as this user.

type OAuthClient = {
  name?: string;
  client_name?: string;
  redirect_uri?: string;
};

type AuthorizationDetails = {
  client?: OAuthClient | null;
  redirect_url?: string;
  redirect_to?: string;
  scope?: string;
};

// Narrow typed shim: @supabase/supabase-js `auth.oauth` is beta and may not be
// visible to TypeScript on the installed version. Fall back through `unknown`.
type OAuthApi = {
  getAuthorizationDetails: (id: string) => Promise<{ data: AuthorizationDetails | null; error: { message: string } | null }>;
  approveAuthorization: (id: string) => Promise<{ data: { redirect_url?: string; redirect_to?: string } | null; error: { message: string } | null }>;
  denyAuthorization: (id: string) => Promise<{ data: { redirect_url?: string; redirect_to?: string } | null; error: { message: string } | null }>;
};

function oauthApi(): OAuthApi {
  const client = supabase as unknown as { auth: { oauth: OAuthApi } };
  return client.auth.oauth;
}

export const Route = createFileRoute("/.lovable/oauth/consent")({
  // Browser-only: Supabase reads its session from localStorage, which is
  // absent during SSR.
  ssr: false,
  validateSearch: (s: Record<string, unknown>) => ({
    authorization_id: typeof s.authorization_id === "string" ? s.authorization_id : "",
  }),
  beforeLoad: async ({ search, location }) => {
    if (!search.authorization_id) throw new Error("Missing authorization_id");
    const { data } = await supabase.auth.getSession();
    if (!data.session) {
      const next = location.pathname + location.searchStr;
      throw redirect({ to: "/auth", search: { next } });
    }
  },
  loader: async ({ location }) => {
    const authorizationId = new URLSearchParams(location.search).get("authorization_id")!;
    const { data, error } = await oauthApi().getAuthorizationDetails(authorizationId);
    if (error) throw new Error(error.message);
    const immediate = data?.redirect_url ?? data?.redirect_to;
    if (immediate && !data?.client) throw redirect({ href: immediate });
    return data;
  },
  component: Consent,
  errorComponent: ({ error }) => (
    <main className="min-h-screen flex items-center justify-center p-8 text-center">
      <div>
        <h1 className="text-xl font-semibold mb-2">Could not load this authorization request</h1>
        <p className="text-sm opacity-80">{String((error as Error)?.message ?? error)}</p>
      </div>
    </main>
  ),
});

function Consent() {
  const details = Route.useLoaderData();
  const { authorization_id } = Route.useSearch();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clientName = details?.client?.client_name ?? details?.client?.name ?? "an app";

  async function decide(approve: boolean) {
    setBusy(true);
    setError(null);
    const api = oauthApi();
    const { data, error } = approve
      ? await api.approveAuthorization(authorization_id)
      : await api.denyAuthorization(authorization_id);
    if (error) {
      setBusy(false);
      setError(error.message);
      return;
    }
    const target = data?.redirect_url ?? data?.redirect_to;
    if (!target) {
      setBusy(false);
      setError("No redirect returned by the authorization server.");
      return;
    }
    window.location.href = target;
  }

  return (
    <main className="min-h-screen flex items-center justify-center p-6">
      <div className="max-w-md w-full border border-border rounded-xl p-8 space-y-5 bg-card">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            Connect {clientName} to Stack'd
          </h1>
          <p className="text-sm opacity-75 mt-2">
            This lets {clientName} use Stack'd as you — reading your profile,
            focus history, groups, and rooms under the same permissions you have
            in the app.
          </p>
        </div>

        <ul className="text-sm space-y-1 opacity-90">
          <li>• Share your basic profile</li>
          <li>• Read your focus session history</li>
          <li>• Read your focus groups and rooms</li>
        </ul>

        <p className="text-xs opacity-60">
          This does not bypass Stack'd's permissions or backend policies.
        </p>

        {error && (
          <p role="alert" className="text-sm text-red-500">
            {error}
          </p>
        )}

        <div className="flex gap-3">
          <button
            disabled={busy}
            onClick={() => decide(true)}
            className="flex-1 px-4 py-2 rounded-md bg-primary text-primary-foreground font-medium disabled:opacity-50"
          >
            Approve
          </button>
          <button
            disabled={busy}
            onClick={() => decide(false)}
            className="flex-1 px-4 py-2 rounded-md border border-border font-medium disabled:opacity-50"
          >
            Cancel connection
          </button>
        </div>
      </div>
    </main>
  );
}
