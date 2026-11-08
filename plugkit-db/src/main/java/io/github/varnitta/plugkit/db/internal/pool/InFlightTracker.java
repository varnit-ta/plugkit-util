package io.github.varnitta.plugkit.db.internal.pool;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Counts the statements and transactions running on a database so {@code close()} can stop new work
 * and wait for running work to finish.
 *
 * <p>After {@link #closeAndAwait} starts, {@link #enter} rejects new work with
 * {@link IllegalStateException}, except nested calls made by a thread that is already inside, so
 * in-flight transactions can finish. Uses {@link ReentrantLock} rather than {@code synchronized} so
 * waiting virtual threads don't pin their carrier.
 */
final class InFlightTracker {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition drained = lock.newCondition();
    private final ThreadLocal<int[]> depth = ThreadLocal.withInitial(() -> new int[1]);
    private final String poolName;
    private int active;
    private boolean closed;

    InFlightTracker(String poolName) {
        this.poolName = poolName;
    }

    void enter() {
        int[] threadDepth = depth.get();
        lock.lock();
        try {
            if (closed && threadDepth[0] == 0) {
                throw new IllegalStateException("Database pool '" + poolName + "' is closed");
            }
            active++;
            threadDepth[0]++;
        } finally {
            lock.unlock();
        }
    }

    void exit() {
        int[] threadDepth = depth.get();
        lock.lock();
        try {
            active--;
            threadDepth[0]--;
            if (active == 0) {
                drained.signalAll();
            }
        } finally {
            lock.unlock();
        }
        if (threadDepth[0] == 0) {
            depth.remove();
        }
    }

    boolean closeAndAwait(Duration timeout) throws InterruptedException {
        lock.lock();
        try {
            closed = true;
            long remainingNanos = timeout.toNanos();
            while (active > 0) {
                if (remainingNanos <= 0) {
                    return false;
                }
                remainingNanos = drained.awaitNanos(remainingNanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    int active() {
        lock.lock();
        try {
            return active;
        } finally {
            lock.unlock();
        }
    }
}
