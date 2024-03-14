package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    private Lock lock;
    private PriorityQueue<WaitThread> waitQueue;

    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p>
     * <b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
        lock = new Lock();
        waitQueue = new PriorityQueue<>();

        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        lock.acquire();
        long currentTime = Machine.timer().getTime();

        while (!waitQueue.isEmpty() && waitQueue.peek().getWakeTime() >= currentTime) {
            WaitThread WaitThread = waitQueue.poll();
            WaitThread.thread.ready();
        }

        lock.release();
        KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p>
     * <blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param x the minimum number of clock ticks to wait.
     *
     * @see nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        lock.acquire();
        long wakeTime = Machine.timer().getTime() + x;

        WaitThread sleepingThread = new WaitThread(KThread.currentThread(), wakeTime);
        waitQueue.add(sleepingThread);

        sleepingThread.thread.yield();

        sleepingThread.condition2.sleep();
        lock.release();
    }

    private class WaitThread implements Comparable<WaitThread> {
        public KThread thread;
        public long wakeTime;
        public Condition2 condition2;

        public WaitThread(KThread thread, long wakeTime) {
            this.thread = thread;
            this.wakeTime = wakeTime;
            this.condition2 = new Condition2(lock);
        }

        public long getWakeTime() {
            return wakeTime;
        }

        @Override
        public int compareTo(WaitThread other) {
            return Long.compare(wakeTime, other.getWakeTime());
        }
    }
}