package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * A communicator allows threads to synchronously exchange 32-bit messages.
 */
public class Communicator {

    LinkedList<WaitingThread> speakingQueue = new LinkedList<WaitingThread>();
    LinkedList<WaitingThread> listeningQueue = new LinkedList<WaitingThread>();

    private Lock lock;

    public Communicator() {
        lock = new Lock();
    }

    private static class WaitingThread {
        private final Lock lock;
        private final Condition condition;
        private Integer word;

        public WaitingThread(Integer word) {
            this.word = word;
            this.lock = new Lock();
            this.condition = new Condition(this.lock);
        }
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * word to the listener.
     *
     * @param word the integer to transfer.
     */
    public void speak(int word) {
        this.lock.acquire();
        if (!listeningQueue.isEmpty()) {
            WaitingThread listener = listeningQueue.pop();
            word = listener.word;

            listener.lock.acquire();
            listener.condition.wake();
            listener.lock.release();
        } else {
            WaitingThread speaker = new WaitingThread(null);
            speaker.lock.acquire();
            listeningQueue.add(speaker);
            this.lock.release();
            speaker.condition.sleep();
            this.lock.acquire();
            word = speaker.word;
            speaker.lock.release();
        }
        this.lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the word that thread passed to speak().
     *
     * @return the integer transferred.
     */
    public int listen() {
        this.lock.acquire();
        int word;
        if (!speakingQueue.isEmpty()) {
            WaitingThread speaker = speakingQueue.pop();
            word = speaker.word;

            speaker.lock.acquire();
            speaker.condition.wake();
            speaker.lock.release();
        } else {
            WaitingThread listener = new WaitingThread(null);
            listener.lock.acquire();
            listeningQueue.add(listener);
            this.lock.release();
            listener.condition.sleep();
            this.lock.acquire();
            word = listener.word;
            listener.lock.release();
        }
        this.lock.release();
        return word;
    }
}