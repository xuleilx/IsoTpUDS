package MessageTest.IsoTpUds;

import java.util.List;

public interface UDSPacketBuilder {
    List<byte[]> buildFrames(byte[] udsData);
}