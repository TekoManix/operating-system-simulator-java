import java.util.HashMap;

/**
 * VFS (Virtual File System) — routes device calls to the correct underlying device.
 * Maps a VFS-level id to a (Device, device-level id) pair using parallel arrays.
 * Device names are resolved via a HashMap, so they are not hard-coded.
 */
public class VFS implements Device {
    private Device[] devices = new Device[20];  // Which device each VFS slot maps to
    private int[] deviceIds = new int[20];      // The device-level id for each VFS slot
    private HashMap<String, Device> deviceMap = new HashMap<>(); // Maps name -> device instance

    public VFS() {
        // Register the available devices by name
        deviceMap.put("random", new RandomDevice());
        deviceMap.put("file", new FakeFileSystem());

        // Initialize all device ids to -1 (unused)
        for (int i = 0; i < deviceIds.length; i++) {
            deviceIds[i] = -1;
        }
    }

    /**
     * Opens a device. The first word of the input string selects the device;
     * the remainder is passed to that device's Open().
     * Example: "random 100" -> device = RandomDevice, passed string = "100"
     * @return a VFS-level id, or -1 on failure
     */
    @Override
    public int Open(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }

        // Split on first space: first word is device name, rest is passed to the device
        String deviceName;
        String remainder;
        int spaceIndex = s.indexOf(' ');
        if (spaceIndex == -1) {
            deviceName = s;
            remainder = "";
        } else {
            deviceName = s.substring(0, spaceIndex);
            remainder = s.substring(spaceIndex + 1);
        }

        // Look up the device by name
        Device device = deviceMap.get(deviceName);
        if (device == null) {
            return -1; // Unknown device
        }

        // Ask the device to open with the remainder string
        int devId = device.Open(remainder);
        if (devId == -1) {
            return -1; // Device couldn't open
        }

        // Find an empty VFS slot and record the mapping
        for (int i = 0; i < devices.length; i++) {
            if (devices[i] == null) {
                devices[i] = device;
                deviceIds[i] = devId;
                return i;
            }
        }

        // No VFS slots available — close what we just opened on the device
        device.Close(devId);
        return -1;
    }

    /**
     * Closes a VFS slot — delegates to the underlying device, then frees the slot.
     */
    @Override
    public void Close(int id) {
        if (id >= 0 && id < devices.length && devices[id] != null) {
            devices[id].Close(deviceIds[id]);
            devices[id] = null;
            deviceIds[id] = -1;
        }
    }

    /**
     * Reads from the device mapped to the given VFS id.
     */
    @Override
    public byte[] Read(int id, int size) {
        if (id >= 0 && id < devices.length && devices[id] != null) {
            return devices[id].Read(deviceIds[id], size);
        }
        return null;
    }

    /**
     * Seeks on the device mapped to the given VFS id.
     */
    @Override
    public void Seek(int id, int to) {
        if (id >= 0 && id < devices.length && devices[id] != null) {
            devices[id].Seek(deviceIds[id], to);
        }
    }

    /**
     * Writes to the device mapped to the given VFS id.
     */
    @Override
    public int Write(int id, byte[] data) {
        if (id >= 0 && id < devices.length && devices[id] != null) {
            return devices[id].Write(deviceIds[id], data);
        }
        return 0;
    }
}
