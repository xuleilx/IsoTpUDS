package MessageTest.IsoTpUds;


public interface UDSPacketParser {
    void feedFrame(byte[] frame);  // 每次喂一个ISO-TP帧
    void reset();                  // 清空组包状态
    void setCallback(UDSReassemblyCallback callback);
    // 回调接口：组包完成时通知上层
    public interface UDSReassemblyCallback {
        void onComplete(byte[] fullUdsData);
        void onError(String reason);
    }
}