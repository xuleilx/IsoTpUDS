package MessageTest.IsoTpUds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Main {
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
    public static void main(String[] args) {
        byte[] udsData = new byte[]{0x22, (byte) 0xF1, (byte) 0x90};

        // 构造拆包器
        UDSPacketBuilder builder = new IsoTpUDSPacketBuilder();
        List<byte[]> frames = builder.buildFrames(udsData);
        System.out.println("拆分出 " + frames.size() + " 个帧");
        for (byte[] frame : frames) {
            System.out.println("Generated frame: " + bytesToHex(frame));
        }
        // 构造组包器并设置回调
        IsoTpUDSPacketParser parser = new IsoTpUDSPacketParser();
        parser.setCallback(new UDSPacketParser.UDSReassemblyCallback() {
            @Override
            public void onComplete(byte[] fullUdsData) {
                System.out.println("组包完成: " + bytesToHex(fullUdsData));
            }

            @Override
            public void onError(String reason) {
                System.out.println("组包错误: " + reason);
            }
        });

        // 模拟接收帧（顺序喂入）
        for (byte[] frame : frames) {
            parser.feedFrame(frame);
        }

        ArrayList<byte[]> list = new ArrayList<>();
        list.add(new byte[]{0x10, 0x1B, 'T', 'h', 'i', 's', ' ', 'a'});
        list.add(new byte[]{0x21, ' ', 'l', 'o', 'n', 'g', ' ', 'm'});
        // 缺0x22帧
        list.add(new byte[]{0x23, ' ', '1', '2', '3', '4', '5', '6'});
        for (byte[] frame : list) {
            parser.feedFrame(frame);
        }
        list.clear();
        list.add(new byte[]{0x10, 0x1B, 'T', 'h', 'i', 's', ' ', 'a'});
        list.add(new byte[]{0x21, ' ', 'l', 'o', 'n', 'g', ' ', 'm'});
        list.add(new byte[]{0x22, ' ', 'm', 'e', 's', 's', 'a', 'g'});
        list.add(new byte[]{0x23, ' ', '1', '2', '3', '4', '5', '6'});
        for (byte[] frame : list) {
            parser.feedFrame(frame);
        }
    }
}
