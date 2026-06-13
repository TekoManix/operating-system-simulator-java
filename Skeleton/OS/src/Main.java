/**
 * Main entry point for the OS simulator.
 * Boots with Init, which spawns test processes at different priorities.
 */
public class Main {
    public static void main(String[] args) {
        OS.Startup(new UserlandProcess[] { new Init() });
    }
}
