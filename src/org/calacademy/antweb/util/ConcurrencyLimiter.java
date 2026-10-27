package org.calacademy.antweb.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A limit on how many requests may do one expensive thing at the same time (bigMap.do, the comparison
 * pages of browse.do, fieldGuide.do). Thread-safe; the count only goes down for a request that got a slot
 * (2026-10-26, public-pages finding F2: the counters were plain static ints and could drift).
 *
 * Use:
 * <pre>
 *   boolean held = limiter.tryAcquire();
 *   if (!held) return busyMessage;
 *   try { ... } finally { if (held) limiter.release(); }
 * </pre>
 * Logged-in users are not limited: the caller skips tryAcquire for them (and so never releases).
 */
public final class ConcurrencyLimiter {

    private final int limit;
    private final AtomicInteger count = new AtomicInteger(0);

    /** @param limit the number of requests that may run at the same time (at least 1). */
    public ConcurrencyLimiter(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be at least 1: " + limit);
        this.limit = limit;
    }

    /** Takes a slot if one is free. Returns false (and takes nothing) when all slots are in use. */
    public boolean tryAcquire() {
        while (true) {
            int c = count.get();
            if (c >= limit) return false;
            if (count.compareAndSet(c, c + 1)) return true;
        }
    }

    /** Gives back a slot taken with tryAcquire(). Never goes below zero. */
    public void release() {
        count.updateAndGet(c -> c > 0 ? c - 1 : 0);
    }

    public int inUse() {
        return count.get();
    }

    public int getLimit() {
        return limit;
    }
}
