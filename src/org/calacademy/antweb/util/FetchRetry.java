package org.calacademy.antweb.util;

/**
 * Tries a download again after a pause (2026-10-25). On 10-23 the nightly AntCat import could not fetch
 * antcat.antweb.txt from the export server once, so that night's name update was skipped; the next nights
 * worked. The nightly now tries again every 10 minutes for up to an hour before it gives up.
 * Admin runs ("Import AntCat names now") try once: they run inside a web request.
 */
public final class FetchRetry {

    private FetchRetry() {}

    /** First try plus 6 more, 10 minutes apart: gives up about an hour after the first failure. */
    public static final int NIGHTLY_ATTEMPTS = 7;
    public static final long NIGHTLY_WAIT_MS = 10 * 60 * 1000L;

    /** One try. Returns null when it worked, otherwise why it failed. */
    public interface Attempt {
        String run();
    }

    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    public static final Sleeper THREAD_SLEEP = Thread::sleep;

    public static final class Result {
        /** How many tries were made. */
        public final int attempts;
        /** Null when the last try worked, otherwise the reason the last try failed. */
        public final String error;

        Result(int attempts, String error) {
            this.attempts = attempts;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }
    }

    public static Result run(Attempt attempt, int maxAttempts, long waitMs, Sleeper sleeper) {
        if (maxAttempts < 1) maxAttempts = 1;
        String error = null;
        for (int i = 1; i <= maxAttempts; i++) {
            error = attempt.run();
            if (error == null) return new Result(i, null);
            if (i < maxAttempts) {
                try {
                    sleeper.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Result(i, error + " (interrupted while waiting to try again)");
                }
            }
        }
        return new Result(maxAttempts, error);
    }
}
