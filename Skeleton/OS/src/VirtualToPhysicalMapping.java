public class VirtualToPhysicalMapping {
    public int physicalPage; // -1 if not in physical memory
    public int diskPage; // -1 if not on disk

    public VirtualToPhysicalMapping(){
        this.physicalPage = -1;
        this.diskPage = -1;
    }
}
