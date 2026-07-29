import { auth, defineMcp } from "@lovable.dev/mcp-js";
import getProfile from "./tools/get-profile";
import listFocusHistory from "./tools/list-focus-history";
import listGroups from "./tools/list-groups";
import listMyRooms from "./tools/list-my-rooms";

// The OAuth issuer must be the direct Supabase host. On publish, SUPABASE_URL is
// rewritten to the `.lovable.cloud` proxy, which mcp-js rejects (RFC 8414 issuer
// mismatch). VITE_SUPABASE_PROJECT_ID is inlined by Vite at build time and
// survives publish unchanged. The fallback keeps the issuer well-formed during
// the throwaway manifest-extract eval; a real token never verifies against it.
const projectRef = import.meta.env.VITE_SUPABASE_PROJECT_ID ?? "project-ref-unset";

export default defineMcp({
  name: "stackd-mcp",
  title: "Stack'd",
  version: "0.1.0",
  instructions:
    "Tools for the signed-in Stack'd user. Read the user's profile, focus-session history, focus groups, and their created rooms. All reads run under the user's Supabase RLS policies.",
  auth: auth.oauth.issuer({
    issuer: `https://${projectRef}.supabase.co/auth/v1`,
    acceptedAudiences: "authenticated",
  }),
  tools: [getProfile, listFocusHistory, listGroups, listMyRooms],
});
