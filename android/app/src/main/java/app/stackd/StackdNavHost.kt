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
import app.stackd.feature.achievements.AchievementsRoute
import app.stackd.feature.insights.DnaRoute
import app.stackd.feature.feed.FeedRoute
import app.stackd.feature.friends.FriendsRoute
import app.stackd.feature.groups.CirclesRoute
import app.stackd.feature.groups.GroupsRoute
import app.stackd.feature.timeline.TimelineRoute
import app.stackd.feature.insights.InsightsRoute
import app.stackd.feature.profile.ProfileRoute
import app.stackd.feature.progression.ChallengesRoute
import app.stackd.feature.progression.SeasonsRoute
import app.stackd.feature.vault.CapsuleRoute
import app.stackd.feature.vault.VaultRoute
import app.stackd.feature.leaderboard.LeaderboardRoute
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
                menuEntries = listOf(
                    "Premium" to Dest.Premium,
                    "Feed" to Dest.Feed,
                    "Timeline" to Dest.Timeline,
                    "Circles" to Dest.Circles,
                    "Groups" to Dest.Groups,
                    "Leaderboard" to Dest.Leaderboard,
                    "Achievements" to Dest.Achievements,
                    "Insights" to Dest.Insights,
                    "Focus DNA" to Dest.Dna,
                    "Challenges" to Dest.Challenges,
                    "Seasons" to Dest.Seasons,
                    "Memory Vault" to Dest.Vault,
                    "Time Capsule" to Dest.Capsule,
                    "Friends" to Dest.Friends,
                    "Profile" to Dest.Profile,
                ).map { (label, dest) ->
                    label to { navController.navigate(dest.route); Unit }
                },
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
        composable(Dest.Profile.route) {
            ProfileRoute(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Dest.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenPremium = { navController.navigate(Dest.Premium.route) },
            )
        }
        composable(
            route = Dest.ProfileDetail.route,
            arguments = listOf(navArgument(Dest.ProfileDetail.ARG_ID) { type = NavType.StringType }),
        ) { PlaceholderScreen(title = "Profile Detail") }
        composable(Dest.Friends.route) {
            FriendsRoute(onBack = { navController.popBackStack() })
        }
        composable(Dest.Feed.route) {
            FeedRoute(
                onBack = { navController.popBackStack() },
                onStart = { navController.navigate(Dest.Start.route) },
                onOpenFriends = { navController.navigate(Dest.Friends.route) },
            )
        }
        composable(Dest.Timeline.route) {
            TimelineRoute(onBack = { navController.popBackStack() })
        }
        placeholder(Dest.Partners, "Partners")

        // Progression
        composable(Dest.Leaderboard.route) {
            LeaderboardRoute(onBack = { navController.popBackStack() })
        }
        composable(Dest.Achievements.route) {
            AchievementsRoute(onBack = { navController.popBackStack() })
        }
        composable(Dest.Challenges.route) {
            ChallengesRoute(onBack = { navController.popBackStack() })
        }
        composable(Dest.Seasons.route) {
            SeasonsRoute(onBack = { navController.popBackStack() })
        }

        // Groups
        composable(Dest.Circles.route) {
            CirclesRoute(
                onBack = { navController.popBackStack() },
                onManage = { navController.navigate(Dest.Groups.route) },
            )
        }
        composable(Dest.Groups.route) {
            GroupsRoute(
                onBack = { navController.popBackStack() },
                onOpenRoom = { code ->
                    navController.navigate(Dest.Room.of(code)) {
                        // A dispatched sprint drops the host straight into the
                        // lobby; Back should return to the dashboard, not the
                        // groups form.
                        popUpTo(Dest.Dashboard.route)
                    }
                },
            )
        }

        // Analytics & recall
        composable(Dest.Insights.route) {
            InsightsRoute(onBack = { navController.popBackStack() })
        }
        composable(Dest.Dna.route) {
            DnaRoute(
                onBack = { navController.popBackStack() },
                onUpgrade = { navController.navigate(Dest.Premium.route) },
            )
        }
        placeholder(Dest.Replay, "Replay")
        placeholder(Dest.Wrapped, "Wrapped")
        composable(Dest.Vault.route) {
            VaultRoute(
                onBack = { navController.popBackStack() },
                onUpgrade = { navController.navigate(Dest.Premium.route) },
            )
        }
        composable(Dest.Capsule.route) {
            CapsuleRoute(
                onBack = { navController.popBackStack() },
                onUpgrade = { navController.navigate(Dest.Premium.route) },
            )
        }

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
