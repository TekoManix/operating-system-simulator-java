/**
 * Init process - spawns test processes at different priorities, then exits.
 * Tests CreateProcess() and Exit().
 */
public class Init extends UserlandProcess {
    @Override
    public void main() {
        OS.CreateProcess(new DeviceTestProcess(), OS.PriorityType.interactive);
        OS.CreateProcess(new RealtimeHog(), OS.PriorityType.realtime);
        OS.CreateProcess(new RealtimeSleeper(), OS.PriorityType.realtime);
        OS.CreateProcess(new InteractiveProcess(), OS.PriorityType.interactive);
        OS.CreateProcess(new BackgroundProcess(), OS.PriorityType.background);

        // Messaging text processes
        OS.CreateProcess(new Ping(), OS.PriorityType.interactive);
        OS.CreateProcess(new Pong(), OS.PriorityType.interactive);

        // Additional test processes running alongside ping/pong
        OS.CreateProcess(new HelloWorld(), OS.PriorityType.interactive);
        OS.CreateProcess(new GoodbyeWorld(), OS.PriorityType.interactive);

        // Memory / paging test processes
        OS.CreateProcess(new MemoryTestProcess(), OS.PriorityType.interactive);
        OS.CreateProcess(new MemoryTestProcess2(), OS.PriorityType.interactive);

        // Init's job is done
        OS.Exit();
    }
}
