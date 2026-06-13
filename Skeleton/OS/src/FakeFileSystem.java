import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * FakeFileSystem — a device backed by real files using RandomAccessFile.
 * Maintains an array of 10 open file handles.
 */
public class FakeFileSystem implements Device {
    private RandomAccessFile[] files = new RandomAccessFile[10]; // Slots for open files

    /**
     * Opens a file by name. The filename must not be null or empty.
     * Creates a RandomAccessFile in read-write mode ("rw") in the first available slot.
     * @return the slot index, or -1 if no slots are available
     * @throws RuntimeException if the filename is null/empty or the file cannot be opened
     */
    @Override
    public int Open(String s) {
        if (s == null || s.isEmpty()) {
            throw new RuntimeException("FakeFileSystem: filename cannot be null or empty");
        }
        for (int i = 0; i < files.length; i++) {
            if (files[i] == null) {
                try {
                    files[i] = new RandomAccessFile(s, "rw");
                } catch (IOException e) {
                    throw new RuntimeException("FakeFileSystem: could not open file " + s, e);
                }
                return i;
            }
        }
        return -1; // No available slot
    }

    /**
     * Closes the file at the given slot — closes the RandomAccessFile and nulls the entry.
     */
    @Override
    public void Close(int id) {
        if (id >= 0 && id < files.length && files[id] != null) {
            try {
                files[id].close();
            } catch (IOException e) {
                // Best-effort close
            }
            files[id] = null;
        }
    }

    /**
     * Reads up to 'size' bytes from the file at the current position.
     * Returns the byte array with the actual number of bytes read.
     */
    @Override
    public byte[] Read(int id, int size) {
        if (id >= 0 && id < files.length && files[id] != null) {
            try {
                byte[] data = new byte[size];
                int bytesRead = files[id].read(data);
                if (bytesRead == -1) {
                    return new byte[0]; // End of file
                }
                // If we read fewer bytes than requested, return only what was read
                if (bytesRead < size) {
                    byte[] trimmed = new byte[bytesRead];
                    System.arraycopy(data, 0, trimmed, 0, bytesRead);
                    return trimmed;
                }
                return data;
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Seeks to the given byte position in the file.
     */
    @Override
    public void Seek(int id, int to) {
        if (id >= 0 && id < files.length && files[id] != null) {
            try {
                files[id].seek(to);
            } catch (IOException e) {
                // Best-effort seek
            }
        }
    }

    /**
     * Writes the given byte array to the file at the current position.
     * @return the number of bytes written (data.length on success, 0 on failure)
     */
    @Override
    public int Write(int id, byte[] data) {
        if (id >= 0 && id < files.length && files[id] != null) {
            try {
                files[id].write(data);
                return data.length;
            } catch (IOException e) {
                return 0;
            }
        }
        return 0;
    }
}
