import java.util.Arrays;

public class Hardware {
    private static final int PAGE_SIZE = 1024;  // 1KB pages
    private static final int NUM_PAGES = 1024;  // 1024 physical pages
    private static final int MEMORY_SIZE = PAGE_SIZE * NUM_PAGES;   // 1MB

    public static byte[] memory = new byte[MEMORY_SIZE];

    // TLB 2 entries, each is [virtualPage, physicalPage], -1 means empty.
    private static int[][] tlb = new int[2][2];

    static {
        clearTLB();
    }

    /**
     * Simulates LOAD instruction. Translates virtual address
     * to physical via TLB and returns the byte at that location.
     */
    public static byte Read(int address) {
        int virtualPage = address / PAGE_SIZE;
        int offset = address % PAGE_SIZE;

        int physicalPage = findPhysicalPage(virtualPage);
        if (physicalPage == -1) {
            // TLB miss - ask the OS to load mapping
            OS.GetMapping(virtualPage);
            physicalPage = findPhysicalPage(virtualPage);
        }

        // If still -1 after GetMapping, process was killed (seg fault)
        // return 0 as a safety net (process won't use this value
        if (physicalPage == -1) {
            return 0;
        }

        int physicalAddress = physicalPage * PAGE_SIZE + offset;
        return memory[physicalAddress];
    }

    /**
     * Simulates STORE instruction. Translates virtual address
     * to physical via TLB and writes the byte at that location.
     */
    public static void Write(int address, byte value) {
        int virtualPage = address / PAGE_SIZE;
        int offset = address % PAGE_SIZE;

        int physicalPage = findPhysicalPage(virtualPage);
        if (physicalPage == -1) {
            // TLB miss - ask the OS to load mapping
            OS.GetMapping(virtualPage);
            physicalPage = findPhysicalPage(virtualPage);
        }

        if (physicalPage == -1) {
            return;
        }

        int physicalAddress = physicalPage * PAGE_SIZE + offset;
        memory[physicalAddress] = value;
    }

    /**
     * Searches the TLB for the given virtual page number.
     * @return the physical page number if found, -1 otherwise.
     */
    private static int findPhysicalPage(int virtualPage) {
        for (int i = 0; i < 2; i++) {
            if (tlb[i][0] == virtualPage) {
                return tlb[i][1];
            }
        }
        return -1;
    }

    /**
     * Clears TLB. Must be called on every task switch so one process
     * can't see another's physical page mappings.
     */
    public static void clearTLB() {
        for (int[] entry : tlb) {
            Arrays.fill(entry, -1);
        }
    }

    /**
     * Called by the kernel's GetMapping to place a virtual-physical
     * mapping into a random TLB slot.
     */
    public static void setTLBEntry(int virtualPage, int physicalPage) {
        int slot = new java.util.Random().nextInt(2);
        tlb[slot][0] = virtualPage;
        tlb[slot][1] = physicalPage;

    }
}
