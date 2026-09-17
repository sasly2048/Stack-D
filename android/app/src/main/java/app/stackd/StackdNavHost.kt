package app.stackd

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.stackd.core.ui.PlaceholderScreen

@Composable
fun StackdNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Dest.Landing.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // Signed out
        placeholder(Dest.Landing, "Landing")
        placeholder(Dest.Auth, "Auth")
        placeholder(Dest.Philosophy, "Philosophy")

        // Core session loop
        placeholder(Dest.Dashboard, "Dashboard")
        placeholder(Dest.Start, "Start Session")
        composable(
            route = Dest.Room.route,
            arguments = listOf(navArgument(Dest.Room.ARG_CODE) { type = NavType.StringType }),
        ) { entry ->
            val code = entry.arguments?.getString(Dest.Room.ARG_CODE).orEmpty()
            PlaceholderScreen(title = "Room $code")
        }

        // Identity & social
        placeholder(Dest.Profile, "Profile")
        composable(
            route = Dest.ProfileDetail.route,
            arguments = listOf(navArgument(Dest.ProfileDetail.ARG_ID) { type = NavType.StringType }),
        ) { PlaceholderScreen(title = "Profile Detail") }
        placeholder(Dest.Friends, "Friends")
        placeholder(Dest.Feed, "Feed")
        placeholder(Dest.Timeline, "Timeline")
        placeholder(Dest.Partners, "Partners")

        // Progression
        placeholder(Dest.Leaderboard, "Leaderboard")
        placeholder(Dest.Achievements, "Achievements")
        placeholder(Dest.Challenges, "Challenges")
        placeholder(Dest.Seasons, "Seasons")

        // Groups
        placeholder(Dest.Circles, "Circles")
        placeholder(Dest.Groups, "Groups")

        // Analytics & recall
        placeholder(Dest.Insights, "Insights")
        placeholder(Dest.Dna, "Productivity DNA")
        placeholder(Dest.Replay, "Replay")
        placeholder(Dest.Wrapped, "Wrapped")
        placeholder(Dest.Vault, "Memory Vault")
        placeholder(Dest.Capsule, "Time Capsule")

        // Safety
        placeholder(Dest.Trust, "Trust")
        placeholder(Dest.TrustModeration, "Moderation")

        // Assistant — AI wiring deferred, shell ships first
        placeholder(Dest.Companion, "Companion", "AI wiring deferred.")

        // Misc
        placeholder(Dest.Integrations, "Integrations")
        placeholder(Dest.Settings, "Settings")

        // Developer surfaces — reachable only while the Settings toggle is on
        placeholder(Dest.Webhooks, "Webhooks")
        placeholder(Dest.Sdk, "SDK")
        placeholder(Dest.Mcp, "MCP")
    }
}

private fun androidx.navigation.NavGraphBuilder.placeholder(
    dest: Dest,
    title: String,
    note: String? = null,
) = composable(dest.route) { PlaceholderScreen(title = title, note = note) }
