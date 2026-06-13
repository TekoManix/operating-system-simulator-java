/**
 * HelloWorld process - prints "Hello World" in an infinite loop.
 * Demonstrates cooperative multitasking by calling cooperate().
 */
public class HelloWorld extends UserlandProcess {
    @Override
    public void main() {
        while (true) {
            System.out.println("Hello World");
            cooperate();  // Give OS a chance to switch processes
            try {
                Thread.sleep(50);  // Slow down output
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
