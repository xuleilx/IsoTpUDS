package MessageTest.IsoTpUds;

import java.util.ArrayList;
import java.util.List;

public class IsoTpUDSPacketBuilder implements UDSPacketBuilder {
    @Override
    public List<byte[]> buildFrames(byte[] udsData) {
        List<byte[]> frames = new ArrayList<>();
        int totalLength = udsData.length;

        if (totalLength <= 7) {
            // 单帧
            byte[] frame = new byte[totalLength + 1];
            frame[0] = (byte) (0x00 | totalLength); // PCI: 单帧, 长度
            System.arraycopy(udsData, 0, frame, 1, totalLength);
            frames.add(frame);
        } else {

            // 首帧
            byte[] firstFrame = new byte[8];
            firstFrame[0] = (byte) (0x10 | ((totalLength >> 8) & 0x0F));
            firstFrame[1] = (byte) (totalLength & 0xFF);
            System.arraycopy(udsData, 0, firstFrame, 2, 6);
            frames.add(firstFrame);

            // 连续帧
            int remainingLength = totalLength ;
            int seq = 1;
            for (int offset = 6; offset < totalLength; ) {
                remainingLength = totalLength - offset;
                byte[] cf = new byte[8];
                cf[0] = (byte) (0x20 | (seq & 0x0F));
                int len = Math.min(7, remainingLength);
                System.arraycopy(udsData, offset, cf, 1, len);
                frames.add(cf);
                offset += len;
                seq = (seq + 1) % 0x10;
            }
        }
        return frames;
    }
}
