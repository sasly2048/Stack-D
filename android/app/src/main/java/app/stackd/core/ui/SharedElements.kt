package app.stackd.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Shared-element plumbing. The nav host provides the transition scope; each
 * participating destination provides its animated scope. Components (Avatar)
 * opt in with a key, and degrade to a plain render when either is absent —
 * previews, non-participating screens, tests.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks the session loop's recurring objects (the phone stack, the chosen
 * length) as one element across screens, so Home -> lobby and planner ->
 * lobby read as the same thing moving rather than a cut. [morph] uses shared
 * bounds for elements whose content changes shape between screens.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@androidx.compose.runtime.Composable
fun androidx.compose.ui.Modifier.sessionShared(key: String, morph: Boolean = false): androidx.compose.ui.Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    val spec = androidx.compose.animation.BoundsTransform { _, _ ->
        androidx.compose.animation.core.tween(620, easing = app.stackd.core.theme.EaseRitual)
    }
    return with(shared) {
        val state = rememberSharedContentState(key)
        if (morph) {
            this@sessionShared.sharedBounds(state, anim, boundsTransform = spec)
        } else {
            this@sessionShared.sharedElement(state, anim, boundsTransform = spec)
        }
    }
}