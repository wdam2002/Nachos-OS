package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;

import java.util.*;

/**
 * A scheduler that chooses threads based on their priorities.
 * <p/>
 * <p/>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 * <p/>
 * <p/>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 * <p/>
 * <p/>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks,` and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should
     *                         transfer priority from waiting threads
     *                         to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum &&
                priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMaximum)
            return false;

        setPriority(thread, priority + 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;

        setPriority(thread, priority - 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static  int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static  int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static  int priorityMaximum = 7;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);
        
//        System.out.println(thread.getName());

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {

        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
            pq = new java.util.PriorityQueue<KThread>(new KTComparator());
        }

        public void waitForAccess(KThread thread) { // failed to acquire lock . owner of queue = owner of lock
            Lib.assertTrue(Machine.interrupt().disabled());
            ThreadState threadState = getThreadState(thread);
            pq.add(thread);
            threadState.waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            ThreadState threadState = getThreadState(thread);            
            owner = thread;
            threadState.acquire(this);
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            KThread nextThread = pq.poll();
            if (nextThread == null) return null;
            this.acquire(nextThread);
            return nextThread;
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would
         *         return.
         */
        protected ThreadState pickNextThread() {  
        	if (getThreadState(pq.peek()) == null) {return null;}
            return getThreadState(pq.peek());
        }

        public void print() {

        }

        
        private java.util.PriorityQueue<KThread> pq;

        /**
         * A reference to the thread currently holding the resource.
         */
        protected KThread owner = null; // pointer to resource holder.

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority = false;


    }
    
    private class KTComparator implements Comparator<KThread> {
		@Override
		public int compare(KThread kt1, KThread kt2) {
			return getThreadState(kt2).getEffectivePriority() - getThreadState(kt1).getEffectivePriority();
		}
	}
    
//    private class DonationContext {
//	    	
//    	public int getPriority() {
//			return priority;
//		}
//
//		public PriorityQueue getPq() {
//			return pq;
//		}
//		private int priority;
//		private PriorityQueue pq;
//
//		DonationContext(PriorityQueue queue, int priority) {
//    		this.pq = queue;
//    		this.priority = priority;
//			
//		}
//	}

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
		private long timeWaiting;

		/**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;

            this.acquiredQueue = new LinkedList<PriorityQueue>();
            this.waitingFor = null;
            this.donatedPriorities = new Stack<>();

            setPriority(priorityDefault);

        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {

            return effectivePriority;
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param priority the new priority.
         */
        public void setPriority(int priority) {
        	this.priority = priority;
            
            // Initialize originalPriority if it's null (first time setting priority)
            if (originalPriority == null) {
                originalPriority = priority;
            }
            
            // Update the effective priority if it has changed
            if (priority != effectivePriority) {
                effectivePriority = priority;
                hasPriorityChanged = true;
                this.restoreEffectivePriority();
            }
            
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param waitQueue the queue that the associated thread is
         *                  now waiting on.
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {
            this.setTimeWaiting(Machine.timer().getTime());
        	waitingFor = waitQueue;
        	            
            if (waitQueue.owner != null) {
            	this.resolveDonation(waitQueue, this, waitQueue.owner);
            }
            
            
        }
        
        
        private void setTimeWaiting(long time) {
			
		}

		private void resolveDonation(PriorityQueue queue, ThreadState root, KThread other) {		
			
        	ThreadState otherState = getThreadState(other);
        	
        	if(root.getPriority() > otherState.getPriority()) {	
	        	if(queue.transferPriority && waitingFor == null) {
	        		otherState.donatedPriorities.push(root.getPriority());
	        		otherState.adjustPriority(queue);
	        	}
	        	// Donate to the thread that other is waiting on
        		if(otherState.waitingFor != null) {
	        		otherState.resolveDonation(otherState.waitingFor, root, other);
	        	}
        	}
        }

		// For Donation
        private void adjustPriority(PriorityQueue queue) {
        	if (queue.transferPriority) {
	        	this.effectivePriority = this.donatedPriorities.pop();
        	}
		}
        // For Restoration
        private void restoreEffectivePriority() {
            int newEffectivePriority = originalPriority;
            
            // Traverse all acquired queues
            for (PriorityQueue queue : acquiredQueue) {
                // Get the highest priority waiting thread from the queue
                ThreadState nextThread = queue.pickNextThread();
                if (nextThread != null && nextThread.getPriority() > newEffectivePriority) {
                    // If there's a waiting thread with higher priority, update effective priority
                    newEffectivePriority = nextThread.getPriority();
                }
            }
            
            // Update the effective priority of the thread
            effectivePriority = newEffectivePriority;
		}

		/**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            acquiredQueue.add(waitQueue);
            waitingFor = null;
            
//            waitQueue.setTransferPriority(true);

        }

        /**
         * Called when the associated thread has relinquished access to whatever 
         * is guarded by waitQueue.
          * @param waitQueue The waitQueue corresponding to the relinquished resource.
         */
        public void release(PriorityQueue waitQueue) {
            acquiredQueue.remove(waitQueue);
            restoreEffectivePriority();
        }

        public KThread getThread() {
            return thread;
        }


        public long getTimeWaiting() {
			return timeWaiting;
		}

		/**
         * The thread with which this object is associated.
         */
        protected KThread thread;
        /**
         * The priority of the associated thread.
         */
        protected int priority;
        
        protected Integer originalPriority;

        /**
         * True if effective priority has been invalidated for this ThreadState.
         */
        protected boolean hasPriorityChanged = false;
        /**
         * Holds the effective priority of this Thread State.
         */
        protected int effectivePriority = priorityMinimum;

        protected  List<PriorityQueue> acquiredQueue; //keep track of things i own

        protected  PriorityQueue waitingFor; // only one thing to wait for
        
        private Stack<Integer> donatedPriorities;

    }
}