package com.townyelections.model;

/**
 * The lifecycle phase of an {@link Election}.
 */
public enum ElectionPhase {
    /** Residents may register as candidates. */
    NOMINATION,
    /** Residents cast their votes for registered candidates. */
    VOTING,
    /** A tie-breaking runoff round is in progress between the tied candidates. */
    RUNOFF,
    /** The election has finished and a result (or no-winner) has been recorded. */
    CONCLUDED,
    /** The election was cancelled and no result was recorded. */
    CANCELLED
}
