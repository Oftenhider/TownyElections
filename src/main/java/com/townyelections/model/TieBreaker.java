package com.townyelections.model;

import java.util.Locale;

/**
 * Strategy used to resolve a tie between the leading candidates.
 */
public enum TieBreaker {
    /** Pick a random winner among the tied candidates. */
    RANDOM,
    /** The candidate who registered first wins. */
    EARLIEST,
    /** The current mayor wins if they are among the tied; otherwise RANDOM. */
    INCUMBENT,
    /** Start a fresh, short voting round between the tied candidates. */
    RUNOFF,
    /** Declare no winner (election voided). */
    NONE;

    public static TieBreaker fromString(String value, TieBreaker fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return TieBreaker.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
