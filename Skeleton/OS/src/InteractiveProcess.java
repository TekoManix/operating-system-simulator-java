/**
 * InteractiveProcess - runs at interactive priority.
 */
public class InteractiveProcess extends UserlandProcess {
    @Override
    public void main() {
        while (true) {
            cooperate();
        }
    }
}
