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
import app.stackd.feature.auth.AuthRoute
import app.stackd.feature.dashboard.DashboardRoute
import app.stackd.feature.premium.PremiumRoute
import app.stackd.feature.room.RoomRoute
import app.stackd.feature.start.StartRoute

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
        composable(Dest.Auth.route) {
            AuthRoute(
                onAuthenticated = {
                    navController.navigate(Dest.Dashboard.route) {
                        // Signed-out screens leave the back stack — Back from the
                        // dashboard must not return to the auth form.
                        popUpTo(Dest.Landing.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        placeholder(Dest.Philosophy, "Philosophy")

        // Core session loop
        composable(Dest.Dashboard.route) {
            DashboardRoute(
                onStart = { navController.navigate(Dest.Start.route) },
                onOpenRoom = { code -> navController.navigate(Dest.Room.of(code)) },
                onOpenPremium = { navController.navigate(Dest.Premium.route) },
            )
        }
        composable(Dest.Start.route) {
            StartRoute(
                onRoomCreated = { code ->
                    navController.navigate(Dest.Room.of(code)) {
                        // The Start screen is a one-shot configurator; drop it
                        // from the back stack so Back from the room returns to
                        // the dashboard, not to a stale form.
                        popUpTo(Dest.Start.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Dest.Room.route,
            arguments = listOf(navArgument(Dest.Room.ARG_CODE) { type = NavType.StringType }),
        ) { entry ->
            val code = entry.arguments?.getString(Dest.Room.ARG_CODE).orEmpty()
            RoomRoute(
                code = code,
                onExit = {
                    navController.navigate(Dest.Dashboard.route) {
                        popUpTo(Dest.Dashboard.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        // Monetization
        composable(Dest.Premium.route) {
            PremiumRoute(onBack = { navController.popBackStack() })
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
