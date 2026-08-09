/**
 * The interaction contract every control in the app shares.
 *
 * Hover, focus, press and disabled were being re-derived per button, so some
 * had a focus ring and no press feedback, others the reverse, and disabled
 * sometimes still looked clickable. These constants make "all four states" the
 * cheapest thing to type rather than something you remember to add.
 *
 * They compose with existing classNames — append, don't replace.
 */

/** Keyboard focus. Offset so the ring clears the control instead of hugging it. */
export const FOCUS_RING =
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember focus-visible:ring-offset-2 focus-visible:ring-offset-obsidian";

/** Tighter variant for controls sitting inside a dense row. */
export const FOCUS_RING_TIGHT =
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ember";

/**
 * The signature easing, matched to the CSS `--ease-ritual` the rest of the app
 * animates on. Motion that doesn't share an easing curve reads as a different
 * product.
 */
export const TRANSITION = "transition-all duration-200 ease-[var(--ease-ritual)]";

/**
 * Press feedback. Deliberately subtle — 1% is felt more than seen, which is
 * what makes a control feel physical rather than animated.
 * `disabled:active:scale-100` stops a dead button pretending to respond.
 */
export const PRESS = "active:scale-[0.99] disabled:active:scale-100";

/** A disabled control must look unavailable *and* refuse the cursor. */
export const DISABLED = "disabled:opacity-50 disabled:cursor-not-allowed";

/** Everything an interactive control needs. */
export const INTERACTIVE = `cursor-pointer ${TRANSITION} ${PRESS} ${DISABLED} ${FOCUS_RING}`;

/** Same, for controls in dense rows where the offset ring would collide. */
export const INTERACTIVE_TIGHT = `cursor-pointer ${TRANSITION} ${PRESS} ${DISABLED} ${FOCUS_RING_TIGHT}`;

/** Row that is itself a link/button target. */
export const ROW_INTERACTIVE = `${TRANSITION} hover:bg-white/[0.04] ${FOCUS_RING_TIGHT}`;
