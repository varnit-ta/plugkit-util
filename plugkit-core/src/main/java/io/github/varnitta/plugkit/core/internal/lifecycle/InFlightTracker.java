package io.github.varnitta.plugkit.core.internal.lifecycle;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks the work running on a resource, such as a database pool, so the resource can shut down
 * without cutting that work off.
 *
 * <p>Each piece of work calls {@link #startWork()} before it runs and {@link #finishWork()} after.
 * {@link #stopNewWorkAndWait(Duration)} refuses new work and waits until the running work has finished
 * or the timeout passes. A thread that is already running work may still start nested work during
 * shutdown, so a transaction in progress can complete. Uses {@link ReentrantLock} rather than
 * {@code synchronized} so waiting virtual threads don't pin their carrier.
 */
public final class InFlightTracker {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition nothingRunning = lock.newCondition();
    private final ThreadLocal<int[]> nestingPerThread = ThreadLocal.withInitial(() -> new int[1]);
    private final String resourceName;
    private int running;
    private boolean shuttingDown;

    public InFlightTracker(String resourceName) {
        this.resourceName = resourceName;
    }

    public void startWork() {
        int[] nesting = nestingPerThread.get();
        lock.lock();
        try {
            if (shuttingDown && nesting[0] == 0) {
                throw new IllegalStateException(resourceName + " is closed");
            }
            running++;
            nesting[0]++;
        } finally {
            lock.unlock();
        }
    }

    public void finishWork() {
        int[] nesting = nestingPerThread.get();
        lock.lock();
        try {
            running--;
            nesting[0]--;
            if (running == 0) {
                nothingRunning.signalAll();
            }
        } finally {
            lock.unlock();
        }
        if (nesting[0] == 0) {
            nestingPerThread.remove();
        }
    }

    public boolean stopNewWorkAndWait(Duration timeout) throws InterruptedException {
        lock.lock();
        try {
            shuttingDown = true;
            long nanosLeft = timeout.toNanos();
            while (running > 0) {
                if (nanosLeft <= 0) {
                    return false;
                }
                nanosLeft = nothingRunning.awaitNanos(nanosLeft);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public int runningCount() {
        lock.lock();
        try {
            return running;
        } finally {
            lock.unlock();
        }
    }
}
