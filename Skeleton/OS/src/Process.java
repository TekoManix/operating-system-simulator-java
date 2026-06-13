import java.util.concurrent.Semaphore;

public abstract class Process implements Runnable {
    private Thread thread;              // Java thread to run this process
    private Semaphore semaphore;        // Controls when process can run
    private volatile boolean quantumExpired;     // Indicates if quantum has expired

    public Process() {
        // Initialize semaphore with 0 permits - process starts stopped
        semaphore = new Semaphore(0);
        quantumExpired = false;
        // Create and start the thread (it will block on semaphore)
        thread = new Thread(this);
        thread.start();
    }

    // Sets the boolean indicating that this process' quantum has expired
    public void requestStop() {
        quantumExpired = true;
    }

    public abstract void main(); // this is the class your subclasses will implement

    // Returns true if semaphore has 0 permits available
    public boolean isStopped() {
        return semaphore.availablePermits() == 0;
    }

    // Returns true if the Java thread is no longer alive
    public boolean isDone() {
        return !thread.isAlive();
    }

    // Releases (increments) the semaphore, allowing this thread to run
    public void start() {
        semaphore.release();
    }

    // Acquires (decrements) the semaphore, stopping this thread
    public void stop() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Called by the Thread - NEVER CALL THIS DIRECTLY!
    public void run() {
        try {
            // Block until semaphore is released
            semaphore.acquire();
            // Call the actual main method of the process
            main();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // If quantum expired, reset flag and call OS.switchProcess()
    public void cooperate() {
        if (quantumExpired) {
            quantumExpired = false;
            OS.switchProcess();
        }
    }
}
