package app.stackd.feature.room.session

/**
 * Splits a finished session into focused time and abandoned time, exactly as the
 * web room does at finalize (`room.$code.tsx` lines 350–361). Pulled out as a
 * pure function because it decides the score, and a score computed here must
 * equal the web's for the same session — they land in the same table.
 *
 * All arithmetic stays in milliseconds; the caller divides by 1000 only when
 * handing seconds to [FocusScore.compute], matching the web's ms-precision rule.
 *
 * Timestamp provenance (must be preserved for parity):
 *  - [startedAtMillis] is server-set (`started_at`).
 *  - [endedAtMillis] is the host client's clock (`ended_at`), or "now" if the
 *    room row hasn't carried an end time yet.
 *  - [breachAtMillis] is server-set (`breach_at`), present only if breached.
 */
object FinalizeInputs {

    data class Split(val focusMillis: Long, val abandonmentMillis: Long)

    fun compute(
        startedAtMillis: Long,
        endedAtMillis: Long,
        breached: Boolean,
        breachAtMillis: Long?,
    ): Split {
        // No server start ⇒ no measurable session; the web treats this as zero
        // focus rather than trusting a client-only elapsed.
        if (startedAtMillis <= 0L) return Split(0L, 0L)

        val totalElapsed = (endedAtMillis - startedAtMillis).coerceAtLeast(0L)

        // Held the whole way: all of it is focus, nothing abandoned.
        if (!breached || breachAtMillis == null) {
            return Split(focusMillis = totalElapsed, abandonmentMillis = 0L)
        }

        // Broke partway: focus is the time before the breach, abandonment is
        // the time from the breach to the session's end.
        val focus = (breachAtMillis - startedAtMillis).coerceAtLeast(0L)
        val abandonment = (endedAtMillis - breachAtMillis).coerceAtLeast(0L)
        return Split(focusMillis = focus, abandonmentMillis = abandonment)
    }
}
