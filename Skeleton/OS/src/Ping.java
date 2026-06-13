/**
 * Ping process — part of the Ping/Pong messaging test.
 * Finds Pong by name, sends the first message, then loops:
 * waits for a reply, prints it, increments "what", and sends back.
 */
public class Ping extends UserlandProcess {
    @Override
    public void main() {
        // Look up Pong's PID by class name; retry until Pong is running
        int pongPid = -1;
        while (pongPid <= 0) {
            pongPid = OS.GetPidByName("Pong");
            if (pongPid <= 0) {
                cooperate(); // yield so Pong can be scheduled
            }
        }
        System.out.println("I am PING, pong = " + pongPid);

        // Send the first message to kick off the ping-pong exchange
        KernelMessage km = new KernelMessage(pongPid, 0, new byte[0]);
        OS.SendMessage(km);

        // Main loop: wait for reply, print, increment what, send back
        while (true) {
            KernelMessage received = OS.WaitForMessage();
            if (received != null) {
                System.out.println("  PING: from: " + received.getSenderPid()
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
