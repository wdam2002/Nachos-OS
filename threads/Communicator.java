package nachos.threads;

import nachos.machine.*;

/**
 * A communicator allows threads to synchronously exchange 32-bit messages.
 */
public class Communicator {

    private boolean spoken = false;
    private int word;

    private Lock lock = new Lock();
    private Condition2 speaker = new Condition2(lock);
    private Condition2 listener = new Condition2(lock);

    public Communicator() {
        this.spoken = false;
//        this.lock = new Lock();
//        this.speaker = new Condition2(lock);
//        this.listener = new Condition2(lock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * word to the listener.
     *
     * @param word the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire();

        while (spoken) {
//            listener.wake();
            speaker.sleep();
        }

        this.word = word;
        System.out.println(KThread.currentThread().getName() + " spoke " + word);
        spoken = true;
        listener.wake();

        lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the word that thread passed to speak().
     *
     * @return the integer transferred.
     */
    public int listen() {
        lock.acquire();

        while (!spoken) {
            listener.sleep();
        }

        int listenedWord = this.word;
        System.out.println(KThread.currentThread().getName() + " listened " + listenedWord);
        spoken = false;
        speaker.wake();

        lock.release();

        return listenedWord;
    }
}
