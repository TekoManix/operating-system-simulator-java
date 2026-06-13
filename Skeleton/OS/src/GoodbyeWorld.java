/**
 * GoodbyeWorld process - prints "Goodbye World" in an infinite loop.
 * Demonstrates cooperative multitasking by calling cooperate().
 */
public class GoodbyeWorld extends UserlandProcess {
    @Override
    public void main() {
        while (true) {
            System.out.println("Goodbye World");
            cooperate();  // Give OS a chance to switch processes
            try {
                Thread.sleep(50);  // Slow down output
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
