/**
 * DeviceTestProcess — tests all device functionality through the OS API.
 * Exercises: RandomDevice (open, read, seek, close) and
 *            FakeFileSystem (open, write, seek, read, close).
 * Also tests multiple devices open at the same time and two processes
 * connecting to the same device type.
 */
public class DeviceTestProcess extends UserlandProcess {
    @Override
    public void main() {
        System.out.println("=== Device Test Start (PID " + OS.GetPID() + ") ===");

        // --- Test 1: RandomDevice with seed ---
        System.out.println("\n-- Test 1: RandomDevice with seed --");
        int randId = OS.Open("random 42");
        System.out.println("Opened random device with seed 42, id = " + randId);
        byte[] randomBytes = OS.Read(randId, 10);
        System.out.print("Read 10 random bytes: ");
        for (byte b : randomBytes) {
            System.out.print((b & 0xFF) + " ");
        }
        System.out.println();
        cooperate();

        // --- Test 2: RandomDevice seek (advances RNG state) ---
        System.out.println("\n-- Test 2: RandomDevice seek --");
        OS.Seek(randId, 5);
        System.out.println("Seeked past 5 random bytes");
        byte[] moreBytes = OS.Read(randId, 5);
        System.out.print("Read 5 more random bytes: ");
        for (byte b : moreBytes) {
            System.out.print((b & 0xFF) + " ");
        }
        System.out.println();

        // --- Test 3: RandomDevice write (should return 0, does nothing) ---
        System.out.println("\n-- Test 3: RandomDevice write (no-op) --");
        int written = OS.Write(randId, new byte[]{1, 2, 3});
        System.out.println("Write to random returned: " + written + " (expected 0)");
        OS.Close(randId);
        System.out.println("Closed random device");
        cooperate();

        // --- Test 4: RandomDevice without seed ---
        System.out.println("\n-- Test 4: RandomDevice without seed --");
        int randId2 = OS.Open("random");
        System.out.println("Opened random device (no seed), id = " + randId2);
        byte[] unseededBytes = OS.Read(randId2, 5);
        System.out.print("Read 5 random bytes: ");
        for (byte b : unseededBytes) {
            System.out.print((b & 0xFF) + " ");
        }
        System.out.println();
        OS.Close(randId2);
        System.out.println("Closed unseeded random device");
        cooperate();

        // --- Test 5: FakeFileSystem write and read back ---
        System.out.println("\n-- Test 5: FakeFileSystem write/read --");
        int fileId = OS.Open("file testfile.dat");
        System.out.println("Opened file 'testfile.dat', id = " + fileId);
        byte[] message = "Hello from OS!".getBytes();
        int bytesWritten = OS.Write(fileId, message);
        System.out.println("Wrote " + bytesWritten + " bytes to file");

        // Seek back to start and read it back
        OS.Seek(fileId, 0);
        System.out.println("Seeked to position 0");
        byte[] readBack = OS.Read(fileId, bytesWritten);
        System.out.println("Read back: " + new String(readBack));
        cooperate();

        // --- Test 6: Multiple devices open simultaneously ---
        System.out.println("\n-- Test 6: Multiple devices open at once --");
        int r1 = OS.Open("random 100");
        int r2 = OS.Open("random 200");
        int f2 = OS.Open("file testfile2.dat");
        System.out.println("Opened random(100)=" + r1 + ", random(200)=" + r2 + ", file2=" + f2);

        byte[] from1 = OS.Read(r1, 4);
        byte[] from2 = OS.Read(r2, 4);
        System.out.print("Random(100) bytes: ");
        for (byte b : from1) System.out.print((b & 0xFF) + " ");
        System.out.println();
        System.out.print("Random(200) bytes: ");
        for (byte b : from2) System.out.print((b & 0xFF) + " ");
        System.out.println();

        OS.Write(f2, "Second file test".getBytes());
        OS.Seek(f2, 0);
        byte[] f2Read = OS.Read(f2, 16);
        System.out.println("File2 read back: " + new String(f2Read));

        // Close everything
        OS.Close(r1);
        OS.Close(r2);
        OS.Close(f2);
        OS.Close(fileId);
        System.out.println("Closed all devices");

        System.out.println("\n=== Device Test Complete ===");
        OS.Exit();
    }
}
