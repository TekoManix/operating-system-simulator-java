import java.util.Arrays;

public class Kernel extends Process  {
    private Scheduler scheduler;  // The scheduler manages all processes
    private VFS vfs;              // Virtual file system for device management
    private boolean[] freeList = new boolean[1024]; // false = free, true = in use
    private int swapFileId; // VFS 10 for the swap file
    private int nextDiskPage = 0; // Tracks the next available disk page number

    public Kernel(UserlandProcess[] startup) {
        vfs = new VFS();
        swapFileId = vfs.Open("file swapfile"); // Open the swap file via VFS
        if (swapFileId == -1) {
            throw new RuntimeException("Failed to open swap file");
        }
        scheduler = new Scheduler();
        scheduler.setKernel(this); // Scheduler needs kernel ref to close devices on process exit
        // Create PCBs for all startup processes and add to scheduler
        for (UserlandProcess up : startup) {
            PCB pcb = new PCB(up, OS.PriorityType.interactive);
            scheduler.addProcess(pcb);
        }
    }

    // Accessor for the scheduler (needed by OS)
    public Scheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void main() {
            while (true) { // Warning on infinite loop is OK...
                switch (OS.currentCall) { // get a job from OS, do it
                    case SwitchProcess -> SwitchProcess();
                    // Priority Scheduler
                    case Exit -> Exit();
                    case CreateProcess ->
                            OS.retVal = CreateProcess((UserlandProcess) OS.parameters.get(0), (OS.PriorityType) OS.parameters.get(1));
                    case Sleep -> Sleep((int) OS.parameters.get(0));
                    case GetPID -> OS.retVal = GetPid();
                    // Device calls
                    case Open -> OS.retVal = Open((String) OS.parameters.get(0));
                    case Close -> Close((int) OS.parameters.get(0));
                    case Read -> OS.retVal = Read((int) OS.parameters.get(0), (int) OS.parameters.get(1));
                    case Seek -> Seek((int) OS.parameters.get(0), (int) OS.parameters.get(1));
                    case Write -> OS.retVal = Write((int) OS.parameters.get(0), (byte[]) OS.parameters.get(1));
                    // Messaging calls
                    case GetPIDByName -> OS.retVal = GetPidByName((String) OS.parameters.get(0));
                    case SendMessage -> SendMessage((KernelMessage) OS.parameters.get(0));
                    case WaitForMessage -> OS.retVal = WaitForMessage();
                    // Memory calls
                    case GetMapping -> GetMapping((int) OS.parameters.get(0));
                    case AllocateMemory -> OS.retVal = AllocateMemory((int) OS.parameters.get(0));
                    case FreeMemory -> OS.retVal = FreeMemory((int) OS.parameters.get(0), (int) OS.parameters.get(1));
                }
                // Start the next process to run, then stop ourselves
                if (scheduler.currentlyRunning != null) {
                    scheduler.currentlyRunning.start();
                }
                stop();  // Kernel goes to sleep until next OS call
            }
    }

    // Calls scheduler's switchProcess
    private void SwitchProcess() {
        scheduler.switchProcess();
    }

    // Ends the current process — delegates to Scheduler
    private void Exit() {
        scheduler.exit();
    }

    // Creates a new process with the given priority, returns its PID
    private int CreateProcess(UserlandProcess up, OS.PriorityType priority) {
        return scheduler.createProcess(up, priority);
    }

    // Returns VFS
    public VFS getVfs() { return vfs; }

    // Returns swapFileId
    public int getSwapFileId() { return swapFileId; }

    // Returns the next disk page
    public int getNextDiskPage() { return nextDiskPage++; }

    // Puts the current process to sleep for the given milliseconds
    private void Sleep(int mills) {
        scheduler.sleep(mills);
    }

    // Returns the PID of the currently running process
    private int GetPid() {
        return scheduler.getPid();
    }

    /**
     * Opens a device via VFS. Finds an empty slot in the current process's
     * device array, calls VFS open, and stores the VFS id in that slot.
     * @return the userland index into the PCB's device array, or -1 on failure
     */
    private int Open(String s) {
        PCB current = scheduler.getCurrentlyRunning();
        if (current == null) return -1;
        int[] devIds = current.getDeviceIds();
        // Find an empty slot in the process's device array
        int slot = -1;
        for (int i = 0; i < devIds.length; i++) {
            if (devIds[i] == -1) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return -1; // No free slot
        int vfsId = vfs.Open(s);
        if (vfsId == -1) return -1; // VFS couldn't open
        devIds[slot] = vfsId;
        return slot;
    }

    /**
     * Closes a device. Translates the userland id to a VFS id,
     * calls VFS close, then frees the PCB slot.
     */
    private void Close(int id) {
        PCB current = scheduler.getCurrentlyRunning();
        if (current == null) return;
        int[] devIds = current.getDeviceIds();
        if (id < 0 || id >= devIds.length || devIds[id] == -1) return;
        vfs.Close(devIds[id]);
        devIds[id] = -1;
    }

    /**
     * Reads from a device. Translates userland id -> VFS id, then passes through.
     */
    private byte[] Read(int id, int size) {
        PCB current = scheduler.getCurrentlyRunning();
        if (current == null) return null;
        int[] devIds = current.getDeviceIds();
        if (id < 0 || id >= devIds.length || devIds[id] == -1) return null;
        return vfs.Read(devIds[id], size);
    }

    /**
     * Seeks on a device. Translates userland id -> VFS id, then passes through.
     */
    private void Seek(int id, int to) {
        PCB current = scheduler.getCurrentlyRunning();
        if (current == null) return;
        int[] devIds = current.getDeviceIds();
        if (id < 0 || id >= devIds.length || devIds[id] == -1) return;
        vfs.Seek(devIds[id], to);
    }

    /**
     * Writes to a device. Translates userland id -> VFS id, then passes through.
     */
    private int Write(int id, byte[] data) {
        PCB current = scheduler.getCurrentlyRunning();
        if (current == null) return 0;
        int[] devIds = current.getDeviceIds();
        if (id < 0 || id >= devIds.length || devIds[id] == -1) return 0;
        return vfs.Write(devIds[id], data);
    }

    /**
     * Closes a VFS-level device id directly. Called by the scheduler
     * when a process exits to clean up its open devices.
     */
    public void closeDevice(int vfsId) {
        vfs.Close(vfsId);
    }

    private void SendMessage(KernelMessage km) {
        scheduler.sendMessage(km);
    }

    private KernelMessage WaitForMessage() {
        return scheduler.waitForMessage();
    }

    /**
     * Looks up the virtual→physical mapping in the current process's page table
     * and loads it into the TLB. Handles page faults by allocating physical memory
     * or swapping pages. If no mapping exists, the process is killed (seg fault).
     */
    private void GetMapping(int virtualPageNumber) {
        PCB current = scheduler.getCurrentlyRunning();
        if (current == null) return;

        VirtualToPhysicalMapping[] pageTable = current.getPageTable();
        if (virtualPageNumber < 0 || virtualPageNumber >= pageTable.length) {
            System.out.println("seg fault (page " + virtualPageNumber + " out of range) — killing PID " + current.pid);
            scheduler.exit();
            return;
        }

        VirtualToPhysicalMapping mapping = pageTable[virtualPageNumber];
        if (mapping == null) {
            System.out.println("seg fault (unmapped page " + virtualPageNumber + ") — killing PID " + current.pid);
            scheduler.exit();
            return;
        }

        if (mapping.physicalPage == -1) {
            // Page fault: need to allocate physical memory
            int physPage = findFreePhysicalPage();
            if (physPage == -1) {
                // No free physical pages — evict a page from another process
                physPage = scheduler.getRandomProcess();
                if (physPage == -1) {
                    System.out.println("seg fault (no physical memory available) — killing PID " + current.pid);
                    scheduler.exit();
                    return;
                }
            }

            // Now assign the physical page to current process
            mapping.physicalPage = physPage;
            freeList[physPage] = true;  // Mark as in use

            // Load data: from disk if available, else zero the memory
            if (mapping.diskPage != -1) {
                // Read from swap file
                vfs.Seek(getSwapFileId(), mapping.diskPage * 1024);
                byte[] data = vfs.Read(getSwapFileId(), 1024);
                System.arraycopy(data, 0, Hardware.memory, physPage * 1024, 1024);
            } else {
                // Zero the physical memory
                Arrays.fill(Hardware.memory, physPage * 1024, (physPage + 1) * 1024, (byte) 0);
            }
        }

        // Place the mapping into a random TLB slot
        Hardware.setTLBEntry(virtualPageNumber, mapping.physicalPage);
    }


    /**
     * Allocates physical pages and maps them into the process's virtual address space.
     * Finds the first contiguous hole in the virtual page table large enough.
     * @return the starting virtual address, or -1 on failure.
     */
    private int AllocateMemory(int size) {
        if (size <= 0 || size % 1024 != 0) return -1;

        PCB current = scheduler.getCurrentlyRunning();
        if (current == null) return -1;

        int pagesNeeded = size / 1024;
        VirtualToPhysicalMapping[] pageTable = current.getPageTable(); // Updated type

        // Find the first contiguous block of unmapped virtual pages (first fit)
        int startPage = -1;
        int count = 0;
        for (int i = 0; i < pageTable.length; i++) {
            if (pageTable[i] == null) {
                if (count == 0) startPage = i;
                count++;
                if (count == pagesNeeded) break;
            } else {
                count = 0;
                startPage = -1;
            }
        }

        if (count < pagesNeeded) return -1; // Not enough contiguous virtual space

        // For each virtual page in the hole, find a free physical page and map it
        for (int i = 0; i < pagesNeeded; i++) {
            pageTable[startPage + i] = new VirtualToPhysicalMapping();
        }

        return startPage * 1024; // Return starting virtual address
    }

    /**
     * Frees previously allocated memory. Unmaps virtual pages and releases physical pages.
     */
    private boolean FreeMemory(int pointer, int size) {
        if (pointer < 0 || size <= 0 || pointer % 1024 != 0 || size % 1024 != 0) return false;

        PCB current = scheduler.getCurrentlyRunning();
        if (current == null) return false;

        int startPage = pointer / 1024;
        int pageCount = size / 1024;
        VirtualToPhysicalMapping[] pageTable = current.getPageTable();

        if (startPage + pageCount > pageTable.length) return false;

        for (int i = 0; i < pageCount; i++) {
            VirtualToPhysicalMapping m = pageTable[startPage + i];
            if (m != null) {
                if (m.physicalPage != -1) {
                    freeList[m.physicalPage] = false;
                }
                pageTable[startPage + i] = null;
            }
        }
        return true;
    }

    /**
     * Frees all memory owned by a process. Called when a process exits or is killed.
     */
    public void FreeAllMemory(PCB pcb) {
        if (pcb == null) return;
        VirtualToPhysicalMapping[] pageTable = pcb.getPageTable();
        for (int i = 0; i < pageTable.length; i++) {
            if (pageTable[i] != null) {
                if (pageTable[i].physicalPage != -1) {
                    freeList[pageTable[i].physicalPage] = false;
                    pageTable[i].physicalPage = -1;
                }
            }
            // Free the virtual mapping
            pageTable[i] = null;
        }
    }

    /** Finds the first free physical page in the free list. */
    private int findFreePhysicalPage() {
        for (int i = 0; i < freeList.length; i++) {
            if (!freeList[i]) return i;
        }
        return -1; // No free pages
    }

    private int GetPidByName(String name) {
        return scheduler.getPidByName(name);
    }

}
