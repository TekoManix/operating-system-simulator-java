public class KernelMessage {
    private int senderPid;
    private int targetPid;
    private int what;
    private byte[] data;

    public KernelMessage() {
        this.senderPid = 0;
        this.targetPid = 0;
        this.what = 0;
        this.data = new byte[0];
    }

    public KernelMessage(int targetPid, int what, byte[] data) {
        this.senderPid = 0;
        this.targetPid = targetPid;
        this.what = what;
        this.data = (data != null) ? data.clone() : new byte[0];
    }

    public KernelMessage(KernelMessage other) {
        this.senderPid = other.senderPid;
        this.targetPid = other.targetPid;
        this.what = other.what;
        this.data = (other.data != null) ? other.data.clone() : new byte[0];
    }

    public int getSenderPid() {
        return senderPid;
    }

    public void setSenderPid(int senderPid) {
        this.senderPid = senderPid;
    }

    public int getTargetPid() {
        return targetPid;
    }

    public void setTargetPid(int targetPid) {
        this.targetPid = targetPid;
    }

    public int getWhat() {
        return what;
    }

    public void setWhat(int what) {
        this.what = what;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = (data != null) ? data.clone() : new byte[0];
    }

    @Override
    public String toString() {
        return "KernelMessage{from: " + senderPid + ", to: " + targetPid + ", what: " + what + ", dataLen: " + (data != null ? data.length : 0) + "}";
    }
}
