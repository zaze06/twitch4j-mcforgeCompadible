package com.github.twitch4j.util;

public interface IBackoffStrategy {
    /**
     * Sleeps for the delay suggested by {@link #get()}.
     *
     * @return whether the sleep was successful (could be false if maximum attempts have been hit, for example).
     */
    boolean sleep();

    /**
     * Increments the failure count and computes the appropriate exponential backoff.
     *
     * @return the amount of milliseconds to delay before retrying.
     */
    long get();

    /**
     * Resets the failure count for exponential backoff calculations.
     */
    void reset();

    /**
     * Returns the current failure count
     *
     * @return failure count
     */
    int getFailures();
}
