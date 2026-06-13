/**
 * RealtimeSleeper - a realtime process that calls Sleep() regularly.
 * Because it voluntarily sleeps, its timeout counter resets and
 * it should never be demoted.
 */
public class RealtimeSleeper extends UserlandProcess {
    @Override
    public void main() {
        while (true) {
            cooperate();
            OS.Sleep(500);
        }
    }
}
