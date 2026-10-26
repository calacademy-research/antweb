package test.org.calacademy.antweb.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.calacademy.antweb.util.ConcurrencyLimiter;

/**
 * ConcurrencyLimiter (2026-10-26, public-pages finding F2): the limits on simultaneous maps, comparisons
 * and field guides are thread-safe and the count cannot drift.
 */
public class ConcurrencyLimiterTest extends TestCase {

    public void testAllowsUpToTheLimitThenRefuses() {
        ConcurrencyLimiter l = new ConcurrencyLimiter(3);
        assertTrue(l.tryAcquire());
        assertTrue(l.tryAcquire());
        assertTrue(l.tryAcquire());
        assertFalse(l.tryAcquire());
        assertEquals(3, l.inUse());
        l.release();
        assertTrue(l.tryAcquire());
    }

    public void testRefusedRequestTakesNoSlot() {
        ConcurrencyLimiter l = new ConcurrencyLimiter(1);
        assertTrue(l.tryAcquire());
        for (int i = 0; i < 5; i++) assertFalse(l.tryAcquire());
        assertEquals(1, l.inUse());
        l.release();
        assertEquals(0, l.inUse());
    }

    public void testReleaseNeverGoesBelowZero() {
        ConcurrencyLimiter l = new ConcurrencyLimiter(2);
        l.release();
        l.release();
        assertEquals(0, l.inUse());
        assertTrue(l.tryAcquire());
        assertTrue(l.tryAcquire());
        assertFalse(l.tryAcquire());
    }

    public void testRejectsLimitBelowOne() {
        try {
            new ConcurrencyLimiter(0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testManyThreadsNeverExceedTheLimitAndEndAtZero() throws Exception {
        final ConcurrencyLimiter l = new ConcurrencyLimiter(3);
        final AtomicInteger maxSeen = new AtomicInteger();
        final AtomicInteger running = new AtomicInteger();
        final int threads = 16;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 2000; i++) {
                        boolean held = l.tryAcquire();
                        try {
                            if (held) {
                                int r = running.incrementAndGet();
                                maxSeen.accumulateAndGet(r, Math::max);
                                running.decrementAndGet();
                            }
                        } finally {
                            if (held) l.release();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        assertTrue("max running " + maxSeen.get(), maxSeen.get() <= 3);
        assertEquals(0, l.inUse());
    }
}
