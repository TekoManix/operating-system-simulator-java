public class MemoryTestProcess2 extends UserlandProcess {
    @Override
    public void main() {
        System.out.println("[MemTest2] Starting memory isolation test...");

        // Allocate 2KB and fill with a distinct pattern (byte 200)
        int addr = OS.AllocateMemory(2048);
        if (addr == -1) {
            System.out.println("[MemTest2] FAIL: Could not allocate 2KB");
            OS.Exit();
            return;
        }
        System.out.println("[MemTest2] Allocated 2KB at virtual address " + addr);

        for (int i = 0; i < 2048; i++) {
            Hardware.Write(addr + i, (byte) 200);
        }
        cooperate();

        // Wait a bit to let MemTest1 write its own patterns
        for (int round = 0; round < 5; round++) {
            cooperate();
        }

        // Verify our data is untouched by the other process
        boolean pass = true;
        for (int i = 0; i < 2048; i++) {
            byte val = Hardware.Read(addr + i);
            if (val != (byte) 200) {
                System.out.println("[MemTest2] FAIL at offset " + i + ": expected 200, got " + val);
                pass = false;
                break;
            }
        }
        System.out.println("[MemTest2] Isolation test: " + (pass ? "PASS" : "FAIL"));

        OS.FreeMemory(addr, 2048);
        System.out.println("[MemTest2] Freed memory. Done.");
        OS.Exit();
    }
}