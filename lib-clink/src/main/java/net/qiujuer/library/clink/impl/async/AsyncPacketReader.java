package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.ds.BytePriorityNode;
import net.qiujuer.library.clink.frames.AbsSendPacketFrame;
import net.qiujuer.library.clink.frames.CancelSendFrame;
import net.qiujuer.library.clink.frames.SendEntityFrame;
import net.qiujuer.library.clink.frames.SendHeaderFrame;

import java.io.Closeable;
import java.io.IOException;

public class AsyncPacketReader implements Closeable {
    private final PacketProvider provider;
    private IoArgs args = new IoArgs();

    private BytePriorityNode<Frame> node;
    private int nodeSize = 0;

    // 1,2,3...255
    private short lastIdentifier = 0;

    public AsyncPacketReader(PacketProvider provider) {
        this.provider = provider;
    }

    public boolean requestTakePacket() {
        synchronized (this) {
            if (nodeSize >= 1) {
                return true;
            }
        }
        SendPacket packet = provider.takePacket();
        if (packet != null) {
            // am identifier will be generated and put in each frame header at the 5th byte position
            short identifier = generateIdentifier();
            SendHeaderFrame frame = new SendHeaderFrame(identifier, packet);
            // append the first sendHeaderFrame
            appendNewFrame(frame);
        }
        synchronized (this) {
            return nodeSize != 0;
        }
    }

    /**
     * Fill data to IoArgs and prepare next frame to send
     *
     * @return
     */
    public IoArgs fillData() {
        Frame currentFrame = getCurrentFrame();
        if (currentFrame == null) {
            return null;
        }

        try {
            // if sendHeaderFrame, then put its header and body to args
            // if sendEntityFrame, then put its header and body from inputstream to args
            if (currentFrame.handle(args)) {
                // currentFrame has been consumed
                Frame nextFrame = currentFrame.nextFrame(); // if headerFrame => nextFrame is entityFrame
                if (nextFrame != null) {
                    appendNewFrame(nextFrame);
                } else if (currentFrame instanceof SendEntityFrame) {
                    // the last sendEntityFrame
                    provider.completedPacket(((SendEntityFrame) currentFrame).getPacket(), true);
                }
                // popCurrentFrame
                // if nodeSize == 0 then requestTakePacket
                popCurrentFrame();
            }
            return args;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void cancel(SendPacket packet) {
        synchronized (this) {
            if (nodeSize == 0) {
                return;
            }
        }
        for (BytePriorityNode<Frame> x = node, before = null; x != null; before = x, x = x.next) {
            Frame frame = x.item;
            if (frame instanceof AbsSendPacketFrame) {
                AbsSendPacketFrame packetFrame = (AbsSendPacketFrame) frame;
                if (packetFrame.getPacket() == packet) {
                    boolean removable = packetFrame.abort();
                    if (removable) {
                        removeFrame(x, before);
                        if (packetFrame instanceof SendHeaderFrame) {
                            break;
                        }
                    }

                    // add cancel frame, notify receiver
                    CancelSendFrame cancelSendFrame = new CancelSendFrame(packetFrame.getBodyIdentifier());
                    appendNewFrame(cancelSendFrame);

                    // stop by accident, return failure
                    provider.completedPacket(packet, false);
                    break;
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        while (node != null) {
            Frame frame = node.item;
            if (frame instanceof AbsSendPacketFrame) {
                SendPacket packet = ((AbsSendPacketFrame) frame).getPacket();
                provider.completedPacket(packet, false);
            }
            node = node.next;
        }

        nodeSize = 0;
        node = null;
    }

    private synchronized void appendNewFrame(Frame frame) {
        BytePriorityNode<Frame> newNode = new BytePriorityNode<>(frame);
        if (node != null) {
            node.appendWithPriority(newNode);
        } else {
            node = newNode;
        }
        nodeSize++;
    }

    private synchronized Frame getCurrentFrame() {
        if (node == null) {
            return null;
        }
        return node.item;
    }

    private void popCurrentFrame() {
        node = node.next;
        nodeSize--;
        if (node == null) {
            requestTakePacket();
        }
    }


    private synchronized void removeFrame(BytePriorityNode<Frame> removeNode, BytePriorityNode<Frame> before) {
        if (before == null) {
            // A B C
            // B C
            node = removeNode.next;
        } else {
            // A B C
            // A C
            before.next = removeNode.next;
        }
        nodeSize--;
        if (node == null) {
            requestTakePacket();
        }
    }

    private short generateIdentifier() {
        short identifier = ++lastIdentifier;
        if (identifier == 255) {
            lastIdentifier = 0;
        }
        return identifier;
    }

    interface PacketProvider {
        SendPacket takePacket();

        void completedPacket(SendPacket packet, boolean isSucceed);
    }
}
