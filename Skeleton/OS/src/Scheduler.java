import java.time.Clock;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Scheduler manages processes across three priority queues and handles
 * context switching using a probabilistic model.
 * Uses a timer to implement time-slicing (quantum = 250ms).
 */
public class Scheduler {
    private LinkedList<PCB> realtimeQueue;      // Highest priority
    private LinkedList<PCB> interactiveQueue;   // Medium priority
    private LinkedList<PCB> backgroundQueue;    // Lowest priority
    private LinkedList<PCB> sleepingProcesses;  // Processes that called Sleep()
    private HashMap<Integer, PCB> processMap;   // PID - PCB lookup
    private HashMap<Integer, PCB> waitingForMessage; // Processes blocked waiting for a message
    private Timer timer;                         // Hardware timer simulation
    private Clock clock;                         // System clock for sleep timing
    private Random random;                       // For probabilistic queue selection
    public volatile PCB currentlyRunning;        // Currently executing process (volatile for timer thread visibility)
    private Kernel kernel;                          // Reference to kernel, needed to close devices on process exit

    private static final int DEMOTION_THRESHOLD = 5; // Consecutive timeouts before demotion

    public Scheduler() {
        realtimeQueue = new LinkedList<>();
        interactiveQueue = new LinkedList<>();
        backgroundQueue = new LinkedList<>();
        sleepingProcesses = new LinkedList<>();
        processMap = new HashMap<>();
        waitingForMessage = new HashMap<>();
        clock = Clock.systemDefaultZone();
        random = new Random();
        timer = new Timer();
        currentlyRunning = null;

        // Schedule interrupt every 250ms (quantum)
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    // Capture in local variable so the null-check and the
                    // method call use the SAME reference. Two separate reads
                    // of a shared field can return different values if another
                    // thread writes between them — causing an NPE that
                    // permanently kills the Timer thread.
                    PCB current = currentlyRunning;
                    if (current != null) {
                        current.requestStop();
                    }
                } catch (Exception e) {
                    // Safety net: an uncaught exception in a TimerTask
                    // terminates the Timer thread forever.
                    System.err.println("Timer error: " + e);
                }
            }
        }, 250, 250);
    }

    /**
     * Sets the kernel reference so we can close devices when a process exits.
     */
    public void setKernel(Kernel kernel) {
        this.kernel = kernel;
    }

    /**
     * Returns the currently running PCB (accessor for kernel's device methods).
     */
    public PCB getCurrentlyRunning() {
        return currentlyRunning;
    }

    /**
     * Adds a process to the correct priority queue based on its priority.
     * If nothing is currently running, immediately sets it as current.
     * @param pcb The process control block to add
     */
    public void addProcess(PCB pcb) {
        processMap.put(pcb.pid, pcb);
        addToCorrectQueue(pcb);
    }

    public int getRandomProcess() {
        LinkedList<PCB> allProcesses = new LinkedList<>();
        allProcesses.addAll(realtimeQueue);
        allProcesses.addAll(interactiveQueue);
        allProcesses.addAll(backgroundQueue);
        allProcesses.addAll(sleepingProcesses);
        for (PCB p : waitingForMessage.values()) {
            if (!allProcesses.contains(p)) allProcesses.add(p);
        }
        if (currentlyRunning != null && !allProcesses.contains(currentlyRunning)) {
            allProcesses.add(currentlyRunning);
        }

        while (!allProcesses.isEmpty()) {
            PCB victim = allProcesses.get(random.nextInt(allProcesses.size()));
            VirtualToPhysicalMapping[] pageTable = victim.getPageTable();
            for (int i = 0; i < pageTable.length; i++) {
                if (pageTable[i] != null && pageTable[i].physicalPage != -1) {
                    if (pageTable[i].diskPage == -1) {
                        pageTable[i].diskPage = kernel.getNextDiskPage();
                        kernel.getVfs().Seek(kernel.getSwapFileId(), pageTable[i].diskPage * 1024);
                        byte[] data = new byte[1024];
                        System.arraycopy(Hardware.memory, pageTable[i].physicalPage * 1024, data, 0, 1024);
                        kernel.getVfs().Write(kernel.getSwapFileId(), data);
                    }
                    int physPage = pageTable[i].physicalPage;
                    pageTable[i].physicalPage = -1;
                    return physPage;
                }
            }
            allProcesses.remove(victim);
        }
        return -1;
    }

    /**
     * Creates a new process, adds it to the appropriate queue, and returns its PID.
     */
    public int createProcess(UserlandProcess up, OS.PriorityType priority) {
        PCB pcb = new PCB(up, priority);
        addProcess(pcb); // addProcess handles processMap registration
        return pcb.pid;
    }

    /**
     * Puts the currently running process to sleep for the given milliseconds.
     * The process is moved to the sleeping list and a new process is selected.
     */
    public void sleep(int milliseconds) {
        Hardware.clearTLB();
        if (currentlyRunning != null) {
            // Set the wake-up time: current time + requested sleep duration
            currentlyRunning.setWakeUpTime(clock.millis() + milliseconds);
            // Sleeping voluntarily resets the timeout counter
            currentlyRunning.resetTimeoutCount();
            // Move to sleeping list instead of a run queue
            sleepingProcesses.add(currentlyRunning);
            currentlyRunning = null;
        }
        // Wake any ready sleepers, then pick next process
        wakeUpProcesses();
        currentlyRunning = getNextProcess();
    }

    public void exit() {
        Hardware.clearTLB();
        if (currentlyRunning != null) {
            // Free all memory pages owned by this process
            if (kernel != null) {
                kernel.FreeAllMemory(currentlyRunning);
            }
            // Close all open devices for this process before discarding it
            int[] devIds = currentlyRunning.getDeviceIds();
            for (int i = 0; i < devIds.length; i++) {
                if (devIds[i] != -1 && kernel != null) {
                    kernel.closeDevice(devIds[i]);
                    devIds[i] = -1;
                }
            }
            processMap.remove(currentlyRunning.pid);
            waitingForMessage.remove(currentlyRunning.pid);
        }
        currentlyRunning = null;
        wakeUpProcesses();
        currentlyRunning = getNextProcess();
    }

    /**
     * Returns the PID of the currently running process.
     */
    public int getPid() {
        if (currentlyRunning != null) {
            return currentlyRunning.pid;
        }
        return -1;
    }

    /**
     * Switches from the current process to the next one.
     * Handles demotion for processes that repeatedly run to timeout,
     * wakes sleeping processes, and uses probabilistic queue selection.
     * Also closes devices for any process that has finished.
     */
    public void switchProcess() {
        // CLear TLB since we're switching to a diff. process
        Hardware.clearTLB();

        // Wake up any sleeping processes whose time has come
        wakeUpProcesses();

        // If current process is done, close its devices before discarding
        if (currentlyRunning != null && currentlyRunning.isDone()) {
            // Free all memory pages owned by this process
            if (kernel != null) {
                kernel.FreeAllMemory(currentlyRunning);
            }
            int[] devIds = currentlyRunning.getDeviceIds();
            for (int i = 0; i < devIds.length; i++) {
                if (devIds[i] != -1 && kernel != null) {
                    kernel.closeDevice(devIds[i]);
                    devIds[i] = -1;
                }
            }
            // Remove finished process from pid lookup and waiting maps
            processMap.remove(currentlyRunning.pid);
            waitingForMessage.remove(currentlyRunning.pid);
            currentlyRunning = null;
        }

        // Handle the current process (put it back in the right queue)
        if (currentlyRunning != null && !currentlyRunning.isDone()) {
            // Process ran to timeout — track it for possible demotion
            currentlyRunning.incrementTimeoutCount();

            // If a realtime or interactive process times out too many times, demote it
            if (currentlyRunning.getTimeoutCount() > DEMOTION_THRESHOLD) {
                if (currentlyRunning.getPriority() != OS.PriorityType.background) {
                    System.out.println("Demoting process PID " + currentlyRunning.pid
                            + " from " + currentlyRunning.getPriority());
                    currentlyRunning.demote();
                    System.out.println("  -> now " + currentlyRunning.getPriority());
                }
                currentlyRunning.resetTimeoutCount();
            }

            // Put the process back in its (possibly new) priority queue
            addToCorrectQueue(currentlyRunning);
        }

        // Select the next process to run using the probabilistic model
        currentlyRunning = getNextProcess();
    }

    /**
     * Checks sleeping processes and moves any that are ready to wake
     * back into their correct priority queue.
     */
    private void wakeUpProcesses() {
        long now = clock.millis();
        Iterator<PCB> it = sleepingProcesses.iterator();
        while (it.hasNext()) {
            PCB pcb = it.next();
            if (pcb.getWakeUpTime() <= now) {
                it.remove();
                addToCorrectQueue(pcb);
            }
        }
    }

    /**
     * Places a PCB into the queue matching its current priority level.
     */
    private void addToCorrectQueue(PCB pcb) {
        switch (pcb.getPriority()) {
            case realtime -> realtimeQueue.addLast(pcb);
            case interactive -> interactiveQueue.addLast(pcb);
            case background -> backgroundQueue.addLast(pcb);
        }
    }

    /**
     * Returns the PID of a process with the given name, or -1 if not found.
     * Searches the processMap (all known living processes).
     */
    public int getPidByName(String name) {
        for (PCB pcb : processMap.values()) {
            if (pcb.getName().equals(name)) {
                return pcb.pid;
            }
        }
        return -1;
    }

    /**
     * Sends a message to another process. Makes a copy of the message (process isolation),
     * sets the sender PID to the current process, finds the target PCB, adds the message
     * to the target's queue, and wakes the target if it was waiting for a message.
     */
    public void sendMessage(KernelMessage km) {
        // Copy the message so sender and receiver don't share references
        KernelMessage copy = new  KernelMessage(km);
        // Set sender PID from the currently running process (security + convenience)
        if (currentlyRunning != null) {
            copy.setSenderPid(currentlyRunning.pid);
        }
        // Find the target process by PID
        PCB target = processMap.get(copy.getTargetPid());
        if (target != null) {
            // Add the copied message to the target's queue
            target.getMessageQueue().add(copy);
            // If the target was waiting for a message, wake it up
            if (waitingForMessage.containsKey(target.pid)) {
                waitingForMessage.remove(target.pid);
                target.retVal = copy;
                addToCorrectQueue(target);
            }
        }
    }

    /**
     * Returns the next message for the current process. If the queue has a message,
     * returns it immediately. Otherwise, de-schedules the process (like Sleep) and
     * puts it in the waitingForMessage map until a message arrives.
     */
    public KernelMessage waitForMessage() {
        if (currentlyRunning == null) {
            return null;
        }
        // If there's already a message queued, return it right away
        if (!currentlyRunning.getMessageQueue().isEmpty()) {
            KernelMessage msg = currentlyRunning.getMessageQueue().removeFirst();
            currentlyRunning.retVal = msg;
            return msg;
        }
        // Message not available - de-schedule and wait
        waitingForMessage.put(currentlyRunning.pid, currentlyRunning);
        PCB waiting = currentlyRunning;
        currentlyRunning = null;
        // Wake any sleepers, then pick next process to run
        wakeUpProcesses();
        currentlyRunning = getNextProcess();
        return null; // Caller will get message when re-scheduled

    }
    /**
     * Uses the probabilistic model to pick the next process to run.
     *
     * If realtime processes exist:
     *   6/10 chance -> realtime, 3/10 -> interactive (if any), 1/10 -> background (if any)
     *   Falls back through the tiers if the chosen tier is empty.
     *
     * Else if interactive processes exist:
     *   3/4 chance -> interactive, 1/4 -> background (if any)
     *
     * Else: take from background.
     *
     * @return the next PCB to run, or null if all queues are empty
     */
    private PCB getNextProcess() {
        if (!realtimeQueue.isEmpty()) {
            int r = random.nextInt(10); // 0-9
            if (r < 6) {
                // 6/10: pick realtime
                return realtimeQueue.removeFirst();
            } else if (r < 9) {
                // 3/10: pick interactive if available, else fallback
                if (!interactiveQueue.isEmpty()) return interactiveQueue.removeFirst();
                if (!backgroundQueue.isEmpty()) return backgroundQueue.removeFirst();
                return realtimeQueue.removeFirst();
            } else {
                // 1/10: pick background if available, else fallback
                if (!backgroundQueue.isEmpty()) return backgroundQueue.removeFirst();
                if (!interactiveQueue.isEmpty()) return interactiveQueue.removeFirst();
                return realtimeQueue.removeFirst();
            }
        } else if (!interactiveQueue.isEmpty()) {
            int r = random.nextInt(4); // 0-3
            if (r < 3) {
                // 3/4: pick interactive
                return interactiveQueue.removeFirst();
            } else {
                // 1/4: pick background if available, else interactive
                if (!backgroundQueue.isEmpty()) return backgroundQueue.removeFirst();
                return interactiveQueue.removeFirst();
            }
        } else if (!backgroundQueue.isEmpty()) {
            return backgroundQueue.removeFirst();
        }
        return null; // No processes available
    }
}
