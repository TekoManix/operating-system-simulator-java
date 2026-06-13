import java.util.Arrays;
import java.util.LinkedList;

public class PCB { // Process Control Block
    private static int nextPid = 1;
    public int pid;
    private String name;
    private OS.PriorityType priority;
    private UserlandProcess userlandProcess;  // Reference to the userland process
    private long wakeUpTime;                  // Clock time at which a sleeping process should wake
    private int timeoutCount;                 // Consecutive times process ran to timeout (for demotion)
    private VirtualToPhysicalMapping[] pageTable = new VirtualToPhysicalMapping[100];    // Virtual page -> physical page (-1 = unmapped)
    private int[] deviceIds = new int[10];    // Maps userland device index -> VFS id (-1 = unused)
    private LinkedList<KernelMessage> messageQueue; // Incoming messages for this process
    public Object retVal = null;

    PCB(UserlandProcess up, OS.PriorityType priority) {
        this.userlandProcess = up;
        this.priority = priority;
        this.name = up.getClass().getSimpleName();
        this.wakeUpTime = 0;
        this.timeoutCount = 0;
        this.messageQueue = new LinkedList<>();
        // Initialize all device slots to -1 (no device open)
        Arrays.fill(this.deviceIds, -1);
        // Assign unique PID and increment counter
        this.pid = nextPid;
        nextPid++;
    }

    public String getName() {
        // Return the class name of the userland process
        return userlandProcess.getClass().getSimpleName();
    }

    OS.PriorityType getPriority() {
        return priority;
    }

    // Delegates to userlandprocess' requestStop
    public void requestStop() {
        userlandProcess.requestStop();
    }

    // Calls userlandprocess' stop, then waits until it's actually stopped
    public void stop() {
        userlandProcess.stop();
        // Loop with Thread.sleep() until ulp.isStopped() is true
        while (!userlandProcess.isStopped()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Calls userlandprocess' isDone()
    public boolean isDone() {
        return userlandProcess.isDone();
    }

    // Calls userlandprocess' start()
    void start() {
        userlandProcess.start();
    }

    public void setPriority(OS.PriorityType newPriority) {
        priority = newPriority;
    }

    public long getWakeUpTime() {
        return wakeUpTime;
    }

    public void setWakeUpTime(long wakeUpTime) {
        this.wakeUpTime = wakeUpTime;
    }

    public int getTimeoutCount() {
        return timeoutCount;
    }

    // Increment the consecutive timeout counter
    public void incrementTimeoutCount() {
        timeoutCount++;
    }

    // Reset the timeout counter (called when process yields voluntarily, e.g. Sleep)
    public void resetTimeoutCount() {
        timeoutCount = 0;
    }

    /** Returns the device ID array (userland index -> VFS id). */
    public int[] getDeviceIds() {
        return deviceIds;
    }

    /** Returns the page table for this process (virtual page index -> physical page). */
    public VirtualToPhysicalMapping[] getPageTable() {
        return pageTable;
    }

    /** Returns the message queue for this process. */
    public LinkedList<KernelMessage> getMessageQueue() {
        return messageQueue;
    }

    /**
     * Demotes the process one priority level.
     * realtime -> interactive, interactive -> background.
     * Background processes cannot be demoted further.
     */
    public void demote() {
        switch (priority) {
            case realtime -> priority = OS.PriorityType.interactive;
            case interactive -> priority = OS.PriorityType.background;
            // background stays background
        }
    }
}
