package MessageTest.IsoTpUds;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class IsoTpUDSPacketParser implements UDSPacketParser {
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private int expectedLength = -1;
    private int nextSeq = 1;
    private UDSReassemblyCallback callback;

    @Override
    public void setCallback(UDSReassemblyCallback callback) {
        this.callback = callback;
    }

    @Override
    public void reset() {
        buffer.reset();
        expectedLength = -1;
        nextSeq = 1;
    }

    @Override
    public void feedFrame(byte[] frame) {
        int pciType = (frame[0] & 0xF0) >> 4;

        switch (pciType) {
            case 0x0:  // 单帧
                int len = frame[0] & 0x0F;
                byte[] single = Arrays.copyOfRange(frame, 1, 1 + len);
                callback.onComplete(single);
                break;

            case 0x1:  // 首帧
                reset();
                expectedLength = ((frame[0] & 0x0F) << 8) | (frame[1] & 0xFF);
                buffer.write(frame, 2, 6);
                break;

            case 0x2:  // 连续帧
                int sn = frame[0] & 0x0F;
                if (sn != (nextSeq & 0x0F)) {
                    callback.onError("Sequence number mismatch");
                    reset();
                    return;
                }
                nextSeq++;
                int remain = expectedLength - buffer.size();
                int copyLen = Math.min(7, remain);
                buffer.write(frame, 1, copyLen);

                if (buffer.size() >= expectedLength) {
                    callback.onComplete(buffer.toByteArray());
                    reset();
                }
                break;

            default:
                callback.onError("Unknown PCI type: " + pciType);
                reset();
        }
    }
}
