import java.util.Random;

/**
 * RandomDevice — a device that produces random bytes (like /dev/random in Linux).
 * Maintains an array of 10 Random instances, each opened independently.
 */
public class RandomDevice implements Device {
    private Random[] randoms = new Random[10]; // Slots for open random devices

    /**
     * Opens a new random device in the first available slot.
     * If the string is non-null and non-empty, it is parsed as an integer seed.
     * @return the slot index, or -1 if no slots are available
     */
    @Override
    public int Open(String s) {
        for (int i = 0; i < randoms.length; i++) {
            if (randoms[i] == null) {
                if (s != null && !s.isEmpty()) {
                    randoms[i] = new Random(Integer.parseInt(s));
                } else {
                    randoms[i] = new Random();
                }
                return i;
            }
        }
        return -1; // No available slot
    }

    /**
     * Closes the random device at the given slot by nulling it out.
     */
    @Override
    public void Close(int id) {
        if (id >= 0 && id < randoms.length) {
            randoms[id] = null;
        }
    }

    /**
     * Reads random bytes — creates and fills a byte array of the given size.
     */
    @Override
    public byte[] Read(int id, int size) {
        if (id >= 0 && id < randoms.length && randoms[id] != null) {
            byte[] data = new byte[size];
            randoms[id].nextBytes(data);
            return data;
        }
        return null;
    }

    /**
     * Seek reads 'to' random bytes but discards them (advances the RNG state).
     */
    @Override
    public void Seek(int id, int to) {
        if (id >= 0 && id < randoms.length && randoms[id] != null) {
            byte[] discard = new byte[to];
            randoms[id].nextBytes(discard);
        }
    }

    /**
     * Write does nothing for a random device — returns 0.
     */
    @Override
    public int Write(int id, byte[] data) {
        return 0;
    }
}
