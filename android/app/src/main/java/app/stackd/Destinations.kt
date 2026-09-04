package app.stackd

/**
 * Every screen in the app, mirroring the web app's routes 1:1.
 *
 * The whole surface is declared up front so the information architecture is
 * settled before the screens are filled in — Phase 0 renders placeholders for
 * all of these, and later phases replace them one at a time.
 *
 * Deliberately absent: the web app's `/catalog`, a component showcase used
 * during web development rather than a product screen.
 */
sealed class Dest(val route: String) {

    // --- Signed out -------------------------------------------------------
    data object Landing : Dest("landing")
    data object Auth : Dest("auth")
    data object Philosophy : Dest("philosophy")

    // --- Core session loop ------------------------------------------------
    data object Dashboard : Dest("dashboard")
    data object Start : Dest("start")
    data object Room : Dest("room/{code}") {
        const val ARG_CODE = "code"
        fun of(code: String) = "room/$code"
    }

    // --- Identity & social ------------------------------------------------
    data object Profile : Dest("profile")
    data object ProfileDetail : Dest("profile/{id}") {
        const val ARG_ID = "id"
        fun of(id: String) = "profile/$id"
    }
    data object Friends : Dest("friends")
    data object Feed : Dest("feed")
    data object Timeline : Dest("timeline")
    data object Partners : Dest("partners")

    // --- Progression ------------------------------------------------------
    data object Leaderboard : Dest("leaderboard")
    data object Achievements : Dest("achievements")
    data object Challenges : Dest("challenges")
    data object Seasons : Dest("seasons")

    // --- Groups -----------------------------------------------------------
    data object Circles : Dest("circles")
    data object Groups : Dest("groups")

    // --- Monetization -----------------------------------------------------
    data object Premium : Dest("premium")

    // --- Analytics & recall ----------------------------------------------
    data object Insights : Dest("insights")
    data object Dna : Dest("dna")
    data object Replay : Dest("replay")
    data object Wrapped : Dest("wrapped")
    data object Vault : Dest("vault")
    data object Capsule : Dest("capsule")

    // --- Safety -----------------------------------------------------------
    data object Trust : Dest("trust")
    data object TrustModeration : Dest("trust/moderation")

    // --- Assistant (AI wiring deferred; shells ship first) ----------------
    data object Companion : Dest("companion")

    // --- Misc -------------------------------------------------------------
    data object Integrations : Dest("integrations")
    data object Settings : Dest("settings")

    // --- Developer surfaces, gated behind the Settings toggle -------------
    data object Webhooks : Dest("dev/webhooks")
    data object Sdk : Dest("dev/sdk")
    data object Mcp : Dest("dev/mcp")
}
