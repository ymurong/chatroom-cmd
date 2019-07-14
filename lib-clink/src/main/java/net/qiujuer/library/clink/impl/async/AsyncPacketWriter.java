package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.frames.*;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.HashMap;

public class AsyncPacketWriter implements Closeable {
    private final PacketProvider provider;

    private final HashMap<Short, PacketModel> packetMap = new HashMap<>();
    private final IoArgs args = new IoArgs();
    private volatile Frame frameTemp;

    AsyncPacketWriter(PacketProvider provider) {
        this.provider = provider;
    }


    /**
     * build a IoArgs container
     * if  currentFrame is null =>  return a container with at least FRAME_HEADER_LENGTH size to receive frame header
     * if currentFrame is not null -> return a container enough to put the rest of the frame yet to come
     *
     * @return IoArgs
     */
    synchronized IoArgs takeIoArgs() {
        args.limit(frameTemp == null ? Frame.FRAME_HEADER_LENGTH : frameTemp.getConsumableLength());
        return args;
    }

    /**
     * 消费IoArgs中的数据
     *
     * @param args IoArgs
     */
    synchronized void consumeIoArgs(IoArgs args) {
        if (frameTemp == null) {
            Frame temp;
            do {
                temp = buildNewFrame(args);
            } while (temp == null && args.remained());

            if (temp == null) {
                return;
            }

            frameTemp = temp;
            if (!args.remained()) {
                return;
            }
        }

        Frame currentFrame = frameTemp;
        do {
            try {
                // read frame body
                // if header frame then write header content to header frame property
                // if entity frame then write to channel
                if (currentFrame.handle(args)) {
                    if (currentFrame instanceof ReceiveHeaderFrame) {
                        ReceiveHeaderFrame headerFrame = (ReceiveHeaderFrame) currentFrame;
                        // build packet with header frame body information
                        ReceivePacket packet = provider.takePacket(headerFrame.getPacketType(),
                                headerFrame.getPacketLength(),
                                headerFrame.getPacketHeaderInfo()
                        );
                        // construct packetModel from packet and add it to packetMap
                        appendNewPacket(headerFrame.getBodyIdentifier(), packet);
                    } else if (currentFrame instanceof ReceiveEntityFrame) {
                        completeEntityFrame((ReceiveEntityFrame) currentFrame);
                    }
                    frameTemp = null;
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (args.remained());
    }

    /**
     * @param identifier packet identifier
     * @param packet     itself
     */
    private void appendNewPacket(short identifier, ReceivePacket packet) {
        PacketModel model = new PacketModel(packet);
        packetMap.put(identifier, model);
    }

    /**
     * this is frame header handling logic
     * build a new frame with help the frame header bytes (6 bytes)
     *
     * @param args
     * @return
     */
    private Frame buildNewFrame(IoArgs args) {
        AbsReceiveFrame frame = ReceiveFrameFactory.createInstance(args);
        if (frame instanceof CancelReceiveFrame) {
            cancelReceivePacket(frame.getBodyIdentifier());
            return null;
        } else if (frame instanceof ReceiveEntityFrame) {
            WritableByteChannel channel = getPacketChannel(frame.getBodyIdentifier());
            ((ReceiveEntityFrame) frame).bindPacketChannel(channel);
        }
        return frame;
    }

    private void completeEntityFrame(ReceiveEntityFrame frame) {
        synchronized (packetMap) {
            short identifier = frame.getBodyIdentifier();
            int length = frame.getBodyLength();
            PacketModel packetModel = packetMap.get(identifier);
            // during self closure, packetMap could be empty
            if (packetModel == null) {
                return;
            }
            packetModel.unreceivedlength -= length;
            if (packetModel.unreceivedlength <= 0) {
                provider.completedPacket(packetModel.packet, true);
                packetMap.remove(identifier);
            }
        }
    }

    private WritableByteChannel getPacketChannel(short bodyIdentifier) {
        synchronized (packetMap) {
            PacketModel model = packetMap.get(bodyIdentifier);
            return model == null ? null : model.channel;
        }
    }

    private void cancelReceivePacket(short bodyIdentifier) {
        synchronized (packetMap) {
            PacketModel model = packetMap.get(bodyIdentifier);
            if (model != null) {
                ReceivePacket packet = model.packet;
                provider.completedPacket(packet, false);
            }
        }
    }

    /**
     * 关闭操作，关闭时若当前还有正在接收的Packet，则尝试停止对应的Packet接收
     */
    @Override
    public void close() throws IOException {
        synchronized (packetMap) {
            Collection<PacketModel> values = packetMap.values();
            for (PacketModel value : values) {
                provider.completedPacket(value.packet, false);
            }
            packetMap.clear();
        }
    }

    /**
     * Packet提供者
     */
    interface PacketProvider {

        /**
         * 拿Packet操作
         *
         * @param type       Packet type
         * @param length     Packet length
         * @param headerInfo Packet headerInfo
         * @return via type, length, headerinfo, get a receive packet
         */
        ReceivePacket takePacket(byte type, long length, byte[] headerInfo);

        /**
         * @param packet    receive packet
         * @param isSucceed if successfully receive Packet completely
         */
        void completedPacket(ReceivePacket packet, boolean isSucceed);
    }

    /**
     * a data model to manage packet and channel couple for later recovery
     */
    static class PacketModel {
        final ReceivePacket packet;
        final WritableByteChannel channel;
        volatile long unreceivedlength;

        PacketModel(ReceivePacket<?, ?> packet) {
            this.packet = packet;
            this.channel = Channels.newChannel(packet.open());
            this.unreceivedlength = packet.length();
        }

    }
}
