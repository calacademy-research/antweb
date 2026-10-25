package org.calacademy.antweb.util;

/**
 * Tries a download again after a pause (2026-10-25). On 10-23 the nightly AntCat import could not fetch
 * antcat.antweb.txt from the export server once, so that night's name update was skipped; the next nights
 * worked. The nightly now tries again every 10 minutes, and makes no new try once an hour has passed since
 * the first one.
 *
 * The nightly retries run before set 1 takes its database connection (UtilDataAction.execute,
 * SpeciesListUploader.prefetchWorldantsList): the long connection pool closes a connection held over 60
 * minutes (unreturnedConnectionTimeout=3600, WEB-INF/struts-configDb.xml) and set 1 already takes up to
 * about an hour. Admin runs ("Import AntCat names now") try once: they run inside a web request.
 * No AntwebProps here, so the unit test needs no settings files.
 */
public final class FetchRetry {

    private FetchRetry() {}

    /** No new try once this much time has passed since the first try. */
    public static final long NIGHTLY_DEADLINE_MS = 60 * 60 * 1000L;
    /** The pause between tries. */
    public static final long NIGHTLY_WAIT_MS = 10 * 60 * 1000L;

    /** One try. Returns null when it worked, otherwise why it failed. */
    public interface Attempt {
        String run();
    }

    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    public interface Clock {
        long now();
    }

    public static final Sleeper THREAD_SLEEP = Thread::sleep;
    public static final Clock SYSTEM_CLOCK = System::currentTimeMillis;

    public static final class Result {
        /** How many tries were made. */
        public final int attempts;
        /** Null when the last try worked, otherwise the reason the last try failed. */
        public final String error;
        /** Minutes from the first try to the end of the last one. */
        public final long minutes;

        Result(int attempts, String error, long minutes) {
            this.attempts = attempts;
            this.error = error;
            this.minutes = minutes;
        }

        public boolean ok() {
            return error == null;
        }
    }

    /** One try, no pause. */
    public static Result once(Attempt attempt) {
        return run(attempt, 0, 0, SYSTEM_CLOCK, ms -> {});
    }

    /**
     * Tries, and after a failure waits waitMs and tries again, as long as the next try would start before
     * deadlineMs have passed since the first one. deadlineMs 0: one try.
     */
    public static Result run(Attempt attempt, long deadlineMs, long waitMs, Clock clock, Sleeper sleeper) {
        long start = clock.now();
        int tries = 0;
        String error;
        while (true) {
            error = attempt.run();
            tries++;
            if (error == null) break;
            if (clock.now() + waitMs - start >= deadlineMs) break;   // the next try would start after the deadline
            try {
                sleeper.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                error = error + " (interrupted while waiting to try again)";
                break;
            }
        }
        return new Result(tries, error, (clock.now() - start) / 60000);
    }

    /** The failure message: where from, how many tries over how long, and why the last one failed. */
    public static String failureMessage(String url, Result result) {
        return "Failed to fetch " + url + " after " + result.attempts + (result.attempts == 1 ? " try" : " tries")
            + (result.attempts > 1 ? " over " + result.minutes + " minutes" : "") + ": " + result.error;
    }
}
