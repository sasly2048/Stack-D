package app.stackd.feature.room.session

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Ported 1:1 from the web's `src/lib/focus-score.ts`.
 *
 * Pure Kotlin — no Android types — so the reward maths is unit-testable on the
 * JVM, the same split [BreachRules] uses. A score computed here and a score
 * computed by the web app for the same session must agree exactly; they land in
 * the same `focus_history` table and the same leaderboards.
 */
object FocusScore {

    /**
     * Stamped on every completed session so a historical score stays
     * interpretable under the rules that produced it. Bump whenever tier
     * boundaries, penalties, or XP multipliers change.
     *
     * v2 — tier and XP derive from the *unrounded* score. In v1 the rounded
     * value fed the tier lookup, so a raw 84.5 rounded to 85 and doubled the
     * XP multiplier at a boundary.
     */
    const val SCORING_VERSION = 2

    const val ABANDONMENT_GRACE_SECONDS = 15
    const val MINOR_PENALTY = 10
    const val SEVERE_PENALTY = 40

    enum class Tier(
        val key: String,
        val label: String,
        val hex: Long,
        val multiplier: Double,
    ) {
        FLOW("flow", "Flow State", 0xFF06B6D4, 1.5),
        PRISTINE("pristine", "Pristine Focus", 0xFF10B981, 1.0),
        STEADY("steady", "Steady Ambient", 0xFFF59E0B, 0.5),
        FRAGMENTED("fragmented", "Fragmented Attention", 0xFFF97316, 0.0),
        COMPROMISED("compromised", "Protocol Compromised", 0xFFEF4444, 0.0),
    }

    fun tierForScore(score: Double): Tier = when {
        score >= 95 -> Tier.FLOW
        score >= 85 -> Tier.PRISTINE
        score >= 70 -> Tier.STEADY
        score >= 40 -> Tier.FRAGMENTED
        else -> Tier.COMPROMISED
    }

    data class Result(
        val score: Int,
        val xp: Int,
        val tier: Tier,
        val penalty: Int,
        val abandonmentPenalty: Int,
        /** Echoed back at integer-second precision, for the DB boundary. */
        val focusSecondsInt: Int,
        val scoringVersion: Int = SCORING_VERSION,
    )

    /**
     * `S_focus = clamp(0, 100, (T_focus / T_target) * 100 - Σ penalties)`,
     * `XP = floor(S_focus * (T_focus / 60) * tierMultiplier)`.
     *
     * Durations arrive as fractional seconds (ms precision preserved from the
     * timestamps) and are only floored where integers are handed to Postgres.
     */
    fun compute(
        targetSeconds: Double,
        focusSeconds: Double,
        severeBreaches: Int,
        minorBreaches: Int,
        abandonmentSeconds: Double = 0.0,
    ): Result {
        val target = targetSeconds.coerceAtLeast(1.0)
        val focus = focusSeconds.coerceIn(0.0, target)

        val breachPenalty = severeBreaches * SEVERE_PENALTY + minorBreaches * MINOR_PENALTY
        val abandonmentPenalty =
            floor(abandonmentSeconds - ABANDONMENT_GRACE_SECONDS).coerceAtLeast(0.0).toInt()
        val penalty = breachPenalty + abandonmentPenalty

        val raw = (focus / target) * 100 - penalty
        val rawClamped = raw.coerceIn(0.0, 100.0)

        // Rounding is a display concern and must not decide rewards — tier and
        // XP read the unrounded value, only `score` is rounded.
        val tier = tierForScore(rawClamped)
        return Result(
            score = rawClamped.roundToInt(),
            xp = floor(rawClamped * (focus / 60.0) * tier.multiplier).toInt(),
            tier = tier,
            penalty = breachPenalty,
            abandonmentPenalty = abandonmentPenalty,
            focusSecondsInt = floor(focus).toInt(),
        )
    }
}
