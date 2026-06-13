public class MemoryTestProcess extends UserlandProcess {
    @Override
    public void main() {
        System.out.println("[MemTest1] Starting memory tests...");

        // Test 1: Allocate 2KB, write pattern, read it back
        int addr1 = OS.AllocateMemory(2048); // 2 pages
        if (addr1 == -1) {
            System.out.println("[MemTest1] FAIL: Could not allocate 2KB");
            OS.Exit();
            return;
        }
        System.out.println("[MemTest1] Allocated 2KB at virtual address " + addr1);

        // Write bytes 42 across all 2048 bytes
        for (int i = 0; i < 2048; i++) {
            Hardware.Write(addr1 + i, (byte) 42);
        }
        cooperate();

        // Read back and verify
        boolean pass1 = true;
        for (int i = 0; i < 2048; i++) {
            if (Hardware.Read(addr1 + i) != 42) {
                pass1 = false;
                break;
            }
        }
        System.out.println("[MemTest1] Test 1 (write/read 2KB): " + (pass1 ? "PASS" : "FAIL"));
        cooperate();

        // Test 2: Allocate another 1KB, write different pattern
        int addr2 = OS.AllocateMemory(1024); // 1 page
        if (addr2 == -1) {
            System.out.println("[MemTest1] FAIL: Could not allocate 1KB");
            OS.Exit();
            return;
        }
        System.out.println("[MemTest1] Allocated 1KB at virtual address " + addr2);

        for (int i = 0; i < 1024; i++) {
            Hardware.Write(addr2 + i, (byte) 99);
        }

        // Verify first allocation is still intact
        boolean pass2 = true;
        for (int i = 0; i < 2048; i++) {
            if (Hardware.Read(addr1 + i) != 42) {
                pass2 = false;
                break;
            }
        }
        System.out.println("[MemTest1] Test 2 (first alloc still intact): " + (pass2 ? "PASS" : "FAIL"));
        cooperate();

        // Test 3: Free first allocation, re-allocate, verify it works
        boolean freed = OS.FreeMemory(addr1, 2048);
        System.out.println("[MemTest1] Freed 2KB: " + freed);

        int addr3 = OS.AllocateMemory(2048);
        System.out.println("[MemTest1] Re-allocated 2KB at virtual address " + addr3);

        for (int i = 0; i < 2048; i++) {
            Hardware.Write(addr3 + i, (byte) 77);
        }
        boolean pass3 = true;
        for (int i = 0; i < 2048; i++) {
            if (Hardware.Read(addr3 + i) != 77) {
                pass3 = false;
                break;
            }
        }
        System.out.println("[MemTest1] Test 3 (free and re-alloc): " + (pass3 ? "PASS" : "FAIL"));
        cooperate();

        // Test 4: Access unmapped memory — should seg fault and kill us
        System.out.println("[MemTest1] Test 4: accessing unmapped address 99999 (expect seg fault)...");
        Hardware.Read(99999);
        // If we get here, the seg fault didn't kill us
        System.out.println("[MemTest1] Test 4: FAIL (should have been killed)");
        OS.Exit();
    }
}