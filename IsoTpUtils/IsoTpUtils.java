package MessageTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IsoTpUtils {

    // 单帧最大数据长度（通常为7字节）
    private static final int SINGLE_FRAME_MAX_DATA_LENGTH = 7;
    private static IsoTpCallback isoTpCallback;
    // 存储未完成的多帧数据（按会话ID区分）
    private static Map<Integer, MultiFrameContext> multiFrameMap = new HashMap<>();

    /**
     * ISO-TP发送接口
     *
     * @param data 待发送的数据
     * @return 符合ISO-TP协议的帧列表
     */
    public static List<byte[]> sendIsoTpMessage(byte[] data) {
        List<byte[]> frames = new ArrayList<>();

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data to be sent cannot be null or empty");
        }

        int totalLength = data.length;

        if (totalLength <= SINGLE_FRAME_MAX_DATA_LENGTH) {
            // 单帧（Single Frame, SF）
            byte[] frame = new byte[totalLength + 1]; // 额外1字节用于PCI
            frame[0] = (byte) (0x00 | totalLength); // PCI类型为0x00，低4位表示数据长度
            System.arraycopy(data, 0, frame, 1, totalLength);
            frames.add(frame);
        } else {
            // 多帧（Multi-Frame, MF）
            int offset = 0;

            while (offset < totalLength) {
                int remainingLength = totalLength - offset;
                int frameDataLength = Math.min(remainingLength, SINGLE_FRAME_MAX_DATA_LENGTH);

                byte[] frame = new byte[frameDataLength + 1]; // 额外1字节用于PCI

                if (offset == 0) {
                    // 第一帧（First Frame, FF）
                    frameDataLength -= 1; //首帧PCI长度2字节，数据长度6字节；
                    frame[0] = (byte) (0x10 | ((totalLength >> 8) & 0x0F)); // PCI类型为0x10，高4位表示数据长度
                    frame[1] = (byte) (totalLength & 0xFF); // 数据长度低8位
                    System.arraycopy(data, offset, frame, 2, frameDataLength);
                } else {
                    // 连续帧（Consecutive Frame, CF）
                    int frameIndex = (offset / SINGLE_FRAME_MAX_DATA_LENGTH) + 1;
                    frame[0] = (byte) (0x20 | (frameIndex & 0x0F)); // PCI类型为0x20，低4位表示帧序号
                    System.arraycopy(data, offset, frame, 1, frameDataLength);
                }

                frames.add(frame);
                offset += frameDataLength;
            }
        }

        return frames;
    }

    public interface IsoTpCallback {
        void onDataReceived(byte[] data);
    }

    // 注册回调方法
    public static void registerCallback(IsoTpCallback callback) {
        isoTpCallback = callback;
    }

    /**
     * 接收ISO-TP帧并组包
     *
     * @param sessionId 会话ID（用于区分不同会话）
     * @param frame     收到的单帧数据
     * @return 如果组包完成，返回完整数据；否则返回null
     */
    public static void processIsoTpFrame(int sessionId, byte[] frame) {
        // 解析PCI（Protocol Control Information）
        if (frame == null || frame.length < 1) {
            throw new IllegalArgumentException("Frame is null or too short for PCI");
        }

        byte pci = frame[0];
        int pciType = pci & 0xF0;

        if (pciType == 0x00) {
            // 单帧（Single Frame, SF）
            int dataLength = pci & 0x0F; // 数据长度
            if (dataLength > frame.length - 1 || dataLength < 0) {
                throw new IllegalArgumentException("Invalid single frame length");
            }
            byte[] data = extractData(frame, 1, dataLength);
            if (isoTpCallback != null) {
                isoTpCallback.onDataReceived(data);
            }
        } else if (pciType == 0x10) {
            // 第一帧（First Frame, FF）
            int totalLength = ((pci & 0x0F) << 8) | (frame[1] & 0xFF);
            MultiFrameContext context = new MultiFrameContext();
            context.reset(totalLength);
            context.appendData(frame, 2, frame.length - 2);

            // 更新会话上下文
            multiFrameMap.put(sessionId, context);
        } else if (pciType == 0x20) {
            // 连续帧（Consecutive Frame, CF）
            MultiFrameContext context = multiFrameMap.get(sessionId);
            if (context == null || !context.isInitialized()) {
                throw new IllegalStateException("Received consecutive frame without first frame");
            }
            context.appendData(frame, 1, frame.length - 1);

            // 更新会话上下文
            multiFrameMap.put(sessionId, context);

            // 检查是否完成组包
            if (context.isComplete()) {
                multiFrameMap.remove(sessionId); // 清理会话上下文
                if (isoTpCallback != null) {
                    isoTpCallback.onDataReceived(context.getData());
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported PCI type: " + pciType);
        }
    }

    /**
     * 提取指定范围的数据
     */
    private static byte[] extractData(byte[] frame, int offset, int length) {
        byte[] data = new byte[length];
        System.arraycopy(frame, offset, data, 0, length);
        return data;
    }

    /**
     * 多帧上下文类
     */
    private static class MultiFrameContext {
        private byte[] dataBuffer;
        private int expectedLength;
        private int receivedLength;

        public void reset(int totalLength) {
            this.expectedLength = totalLength;
            this.dataBuffer = new byte[totalLength];
            this.receivedLength = 0;
        }

        public boolean isInitialized() {
            return dataBuffer != null;
        }

        public void appendData(byte[] frame, int offset, int length) {
            System.arraycopy(frame, offset, dataBuffer, receivedLength, length);
            receivedLength += length;
        }

        public boolean isComplete() {
            return receivedLength >= expectedLength;
        }

        public byte[] getData() {
            return dataBuffer;
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    // 打印 multiFrameMap 的函数
    public static void printMultiFrameMap() {
        if (multiFrameMap.isEmpty()) {
            System.out.println("multiFrameMap is empty");
            return;
        }

        for (Map.Entry<Integer, MultiFrameContext> entry : multiFrameMap.entrySet()) {
            int sessionId = entry.getKey();
            MultiFrameContext context = entry.getValue();

            System.out.println("Session ID: " + sessionId);
            System.out.println("Expected Length: " + context.expectedLength);
            System.out.println("Received Length: " + context.receivedLength);
            System.out.println("Data Buffer: " + Arrays.toString(context.dataBuffer));
        }
    }

    public static void main(String[] args) {
        int sessionId = 1;
        IsoTpUtils.registerCallback(new IsoTpCallback() {
            @Override
            public void onDataReceived(byte[] data) {
                System.out.println("Receive data: " + new String(data));
            }
        });

        System.out.println("> 发送数据");
        System.out.println(">>> 单帧数据");
        byte[] single_data = "1234567".getBytes();
        List<byte[]> frames = IsoTpUtils.sendIsoTpMessage(single_data);
        for (byte[] frame : frames) {
            System.out.println("Generated frame: " + bytesToHex(frame));
            IsoTpUtils.processIsoTpFrame(sessionId, frame);
        }
        System.out.println(">>> 多帧数据");
        System.out.println(">>>>>> 边界值：8字节");
        byte[] multi_data = "12345678".getBytes();
        frames = IsoTpUtils.sendIsoTpMessage(multi_data);
        for (byte[] frame : frames) {
            System.out.println("Generated frame: " + bytesToHex(frame));
            IsoTpUtils.processIsoTpFrame(sessionId, frame);
        }

        System.out.println(">>>>>> Sequence 1 到 15");
        multi_data = "123456789012345678901234".getBytes();
        frames = IsoTpUtils.sendIsoTpMessage(multi_data);
        for (byte[] frame : frames) {
            System.out.println("Generated frame: " + bytesToHex(frame));
            IsoTpUtils.processIsoTpFrame(sessionId, frame);
        }
        System.out.println("Total frames: " + frames.size());

        System.out.println(">>>>>> Sequence 大于 15");
        multi_data = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890".getBytes();
        frames = IsoTpUtils.sendIsoTpMessage(multi_data);
        for (byte[] frame : frames) {
            System.out.println("Generated frame: " + bytesToHex(frame));
            IsoTpUtils.processIsoTpFrame(sessionId, frame);
        }
        System.out.println("Total frames: " + frames.size());

        System.out.println("> 接收数据");
        // 单帧示例
        System.out.println(">>> 单帧");
        byte[] singleFrame = new byte[]{0x05, 'H', 'e', 'l', 'l', 'o'};
        IsoTpUtils.processIsoTpFrame(sessionId, singleFrame);

        // 多帧示例
        System.out.println(">>> 多帧");
        System.out.println(">>>>>> 正常序列");
        ArrayList<byte[]> list = new ArrayList<>();
        list.add(new byte[]{0x10, 0x14, 'T', 'h', 'i', 's', ' ', 'a'});
        list.add(new byte[]{0x21, ' ', 'l', 'o', 'n', 'g', ' ', 'm'});
        list.add(new byte[]{0x22, 'm', 'e', 's', 's', 'a', 'g', '.'});
        for (byte[] frame : list) {
            IsoTpUtils.processIsoTpFrame(sessionId, frame);
        }
        System.out.println(">>>>>> 异常序列");
        System.out.println(">>>>>> 丢包");
        // CAN消息不会乱序，丢包。只有某些字节改变，SPI层有校验，错了会丢弃，
        // 应用接收角度看，异常序列通常是指IPC丢弃错误的包。
        list.clear();
        list.add(new byte[]{0x10, 0x1B, 'T', 'h', 'i', 's', ' ', 'a'});
        list.add(new byte[]{0x21, ' ', 'l', 'o', 'n', 'g', ' ', 'm'});
        // 缺0x22帧
        list.add(new byte[]{0x23, ' ', '1', '2', '3', '4', '5', '6'});
        for (byte[] frame : list) {
            IsoTpUtils.processIsoTpFrame(sessionId, frame);
        }
        IsoTpUtils.printMultiFrameMap();
        System.out.println(">>>>>> 重发");
        list.clear();
        list.add(new byte[]{0x10, 0x1B, 'T', 'h', 'i', 's', ' ', 'a'});
        list.add(new byte[]{0x21, ' ', 'l', 'o', 'n', 'g', ' ', 'm'});
        list.add(new byte[]{0x22, ' ', 'm', 'e', 's', 's', 'a', 'g'});
        list.add(new byte[]{0x23, ' ', '1', '2', '3', '4', '5', '6'});
        for (byte[] frame : list) {
            IsoTpUtils.processIsoTpFrame(sessionId, frame);
        }
        IsoTpUtils.printMultiFrameMap();
    }
}