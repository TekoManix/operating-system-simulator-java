/**
 * Pong process — part of the Ping/Pong messaging test.
 * Finds Ping by name, then loops: waits for a message,
 * prints it, increments "what", and sends a reply back.
 */
public class Pong extends UserlandProcess {
    @Override
    public void main() {
        // Look up Ping's PID by class name; retry until Ping is running
        int pingPid = -1;
        while (pingPid <= 0) {
            pingPid = OS.GetPidByName("Ping");
            if (pingPid <= 0) {
                cooperate(); // yield so Ping can be scheduled
            }
        }
        System.out.println("I am PONG, ping = " + pingPid);

        // Main loop: wait for a message, print it, reply with what+1
        while (true) {
            KernelMessage received = OS.WaitForMessage();
            if (received != null) {
                System.out.println("  PONG: from: " + received.getSenderPid()
                        + " to: " + received.getTargetPid()
                        + " what: " + received.getWhat());
                // Respond with what+1
                KernelMessage reply = new KernelMessage(received.getSenderPid(),
                        received.getWhat() + 1, new byte[0]);
                OS.SendMessage(reply);
            }
            cooperate();
        }
    }
}
