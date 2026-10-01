@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.stackd

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import app.stackd.core.ui.PlaceholderScreen
import app.stackd.feature.auth.AuthRoute
import app.stackd.feature.dashboard.DashboardRoute
import app.stackd.feature.achievements.AchievementsRoute
import app.stackd.feature.insights.DnaRoute
import app.stackd.feature.feed.FeedRoute
import app.stackd.feature.friends.FriendsRoute
import app.stackd.feature.groups.CirclesRoute
import app.stackd.feature.groups.GroupsRoute
import app.stackd.feature.integrations.IntegrationsRoute
import app.stackd.feature.partners.PartnersRoute
import app.stackd.feature.profile.ProfileDetailRoute
import app.stackd.feature.recap.ReplayRoute
import app.stackd.feature.recap.WrappedRoute
import app.stackd.feature.trust.ModerationRoute
import app.stackd.feature.trust.TrustRoute
import app.stackd.feature.timeline.TimelineRoute
import app.stackd.feature.insights.InsightsRoute
import app.stackd.feature.profile.ProfileRoute
import app.stackd.feature.progression.ChallengesRoute
import app.stackd.feature.progression.SeasonsRoute
import app.stackd.feature.vault.CapsuleRoute
import app.stackd.feature.vault.VaultRoute
import app.stackd.feature.leaderboard.LeaderboardRoute
import app.stackd.feature.premium.PremiumRoute
import app.stackd.feature.room.LocalOpenProfile
import app.stackd.feature.room.RoomRoute
import app.stackd.feature.start.StartRoute

@Composable
fun StackdNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Dest.Landing.route,
) {
    val openProfile: (String) -> Unit = { id -> navController.navigate(Dest.ProfileDetail.of(id)) }
    // Two motions, both on the web's curves. Tab <-> tab is the web's
    // view-transition (fade + 6dp rise, 260ms): peers, no direction. Pushing
    // deeper slides in from the right and back reverses it, so position in
    // the stack stays legible.
    val ease = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val dur = 260
    val tabRoutes = app.stackd.core.ui.StackdTabs.map { it.route }.toSet()
    fun isTabSwap(from: androidx.navigation.NavBackStackEntry, to: androidx.navigation.NavBackStackEntry) =
        from.destination.route in tabRoutes && to.destination.route in tabRoutes
    val rise = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    val lift = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.roundToPx() }
    val vtIn = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(dur, easing = ease)) +
        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(dur, easing = ease)) { rise }
    val vtOut = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(dur, easing = ease)) +
        androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(dur, easing = ease)) { -lift }
    // Shared elements (avatar list -> profile header) need a common transition
    // scope around the nav host; destinations that take part provide their
    // animated scope via LocalNavAnimatedScope.
    androidx.compose.animation.SharedTransitionLayout {
    androidx.compose.runtime.CompositionLocalProvider(app.stackd.core.ui.LocalSharedTransitionScope provides this) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            if (isTabSwap(initialState, targetState)) vtIn else
            androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(320, easing = ease)) { it / 4 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320, easing = ease))
        },
        exitTransition = {
            if (isTabSwap(initialState, targetState)) vtOut else
            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(320, easing = ease)) { -it / 10 } +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
        },
        popEnterTransition = {
            if (isTabSwap(initialState, targetState)) vtIn else
            androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(320, easing = ease)) { -it / 10 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320, easing = ease))
        },
        popExitTransition = {
            if (isTabSwap(initialState, targetState)) vtOut else
            androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(320, easing = ease)) { it / 4 } +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
        },
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
                onOpenPremium = { navController.navigate(Dest.Premium.route) { launchSingleTop = true } },
                menuEntries = listOf(
                    // Web nav labels the companion chat "Atlas" (to: "/companion").
                    "Atlas" to Dest.Companion,
                    "Premium" to Dest.Premium,
                    "Feed" to Dest.Feed,
                    "Timeline" to Dest.Timeline,
                    "Circles" to Dest.Circles,
                    "Groups" to Dest.Groups,
                    "Leaderboard" to Dest.Leaderboard,
                    "Achievements" to Dest.Achievements,
                    "Insights" to Dest.Insights,
                    "Focus DNA" to Dest.Dna,
                    "Wrapped" to Dest.Wrapped,
                    "Replay" to Dest.Replay,
                    "Challenges" to Dest.Challenges,
                    "Seasons" to Dest.Seasons,
                    "Memory Vault" to Dest.Vault,
                    "Time Capsule" to Dest.Capsule,
                    "Friends" to Dest.Friends,
                    "Partners" to Dest.Partners,
                    "Trust & Safety" to Dest.Trust,
                    "Integrations" to Dest.Integrations,
                    "Profile" to Dest.Profile,
                ).map { (label, dest) ->
                    // Single-top: a double tap on a menu row must not stack two
                    // copies of the same screen.
                    label to { navController.navigate(dest.route) { launchSingleTop = true }; Unit }
                },
            )
        }
        composable(Dest.Start.route) {
            StartRoute(
                onBack = { navController.popBackStack() },
                onJoinRoom = { code -> navController.navigate(Dest.Room.of(code)) },
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
            deepLinks = listOf(navDeepLink { uriPattern = "https://stackd.raghav.studio/room/{${Dest.Room.ARG_CODE}}" }),
        ) { entry ->
            val code = entry.arguments?.getString(Dest.Room.ARG_CODE).orEmpty()
            CompositionLocalProvider(LocalOpenProfile provides openProfile) {
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
        }

        // Monetization
        composable(Dest.Premium.route) {
            PremiumRoute(onBack = { navController.popBackStack() })
        }

        // Identity & social
        composable(Dest.Profile.route) {
            CompositionLocalProvider(app.stackd.core.ui.LocalIsTabRoot provides true) { ProfileRoute(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Dest.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenPremium = { navController.navigate(Dest.Premium.route) },
            ) }
        }
        composable(
            route = Dest.ProfileDetail.route,
            arguments = listOf(navArgument(Dest.ProfileDetail.ARG_ID) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Dest.ProfileDetail.ARG_ID).orEmpty()
            CompositionLocalProvider(app.stackd.core.ui.LocalNavAnimatedScope provides this) {
                ProfileDetailRoute(userId = id, onBack = { navController.popBackStack() })
            }
        }
        composable(Dest.Friends.route) {
            FriendsRoute(onBack = { navController.popBackStack() }, onOpenProfile = openProfile)
        }
        composable(Dest.Feed.route) {
            CompositionLocalProvider(app.stackd.core.ui.LocalIsTabRoot provides true) { FeedRoute(
                onBack = { navController.popBackStack() },
                onStart = { navController.navigate(Dest.Start.route) },
                onOpenFriends = { navController.navigate(Dest.Friends.route) },
                onOpenProfile = openProfile,
            ) }
        }
        composable(Dest.Timeline.route) {
            TimelineRoute(onBack = { navController.popBackStack() })
        }
        composable(Dest.Partners.route) {
            PartnersRoute(onBack = { navController.popBackStack() })
        }

        // Progression
        composable(Dest.Leaderboard.route) {
            CompositionLocalProvider(app.stackd.core.ui.LocalNavAnimatedScope provides this) { LeaderboardRoute(onBack = { navController.popBackStack() }, onOpenProfile = openProfile) }
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
                onOpenProfile = openProfile,
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
            CompositionLocalProvider(app.stackd.core.ui.LocalIsTabRoot provides true) { InsightsRoute(
                onBack = { navController.popBackStack() },
                onStart = { navController.navigate(Dest.Start.route) },
            ) }
        }
        composable(Dest.Dna.route) {
            DnaRoute(
                onBack = { navController.popBackStack() },
                onUpgrade = { navController.navigate(Dest.Premium.route) },
            )
        }
        composable(Dest.Replay.route) {
            ReplayRoute(onBack = { navController.popBackStack() })
        }
        composable(Dest.Wrapped.route) {
            WrappedRoute(onBack = { navController.popBackStack() })
        }
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
        composable(Dest.Trust.route) {
            TrustRoute(
                onBack = { navController.popBackStack() },
                onOpenModeration = { navController.navigate(Dest.TrustModeration.route) },
            )
        }
        composable(Dest.TrustModeration.route) {
            ModerationRoute(onBack = { navController.popBackStack() })
        }

        // Assistant — the AI study companion chat, backed by the public AI route.
        composable(Dest.Companion.route) {
            app.stackd.feature.companion.CompanionRoute(onBack = { navController.popBackStack() })
        }

        // Misc
        composable(Dest.Integrations.route) {
            IntegrationsRoute(
                onBack = { navController.popBackStack() },
                onOpenWebhooks = { navController.navigate(Dest.Webhooks.route) { launchSingleTop = true } },
            )
        }
        composable(Dest.Webhooks.route) {
            app.stackd.feature.webhooks.WebhooksRoute(onBack = { navController.popBackStack() })
        }
        placeholder(Dest.Settings, "Settings")

        // Developer surfaces — reachable only while the Settings toggle is on
        placeholder(Dest.Sdk, "SDK")
        placeholder(Dest.Mcp, "MCP")
    }
    }
    }
}

private fun androidx.navigation.NavGraphBuilder.placeholder(
    dest: Dest,
    title: String,
    note: String? = null,
) = composable(dest.route) { PlaceholderScreen(title = title, note = note) }
