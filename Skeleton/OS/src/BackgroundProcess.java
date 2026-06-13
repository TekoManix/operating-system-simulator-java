/**
 * BackgroundProcess - runs at background priority.
 */
public class BackgroundProcess extends UserlandProcess {
    @Override
    public void main() {
        while (true) {
            cooperate();
        }
    }
}
