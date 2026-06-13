/**
 * RealtimeHog - a realtime process that never calls Sleep().
 * It will run to timeout every quantum, so it should be demoted
 * after 5 consecutive timeouts (realtime -> interactive -> background).
 */
public class RealtimeHog extends UserlandProcess {
    @Override
    public void main() {
        while (true) {
            cooperate();
        }
    }
}
