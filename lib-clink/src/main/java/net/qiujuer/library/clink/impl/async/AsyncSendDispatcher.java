package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendDispatcher;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSendDispatcher implements SendDispatcher, IoArgs.IoArgsEventProcessor, AsyncPacketReader.PacketProvider {
    private final Sender sender;
    private final Queue<SendPacket> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final AsyncPacketReader reader = new AsyncPacketReader(this);

    /**
     * @param sender SocketChannelAdapter
     */
    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        sender.setSendListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        // put packet in queue
        queue.offer(packet);
        requestSend();
    }

    @Override
    public void cancel(SendPacket packet) {
        boolean ret = queue.remove(packet);
        if (ret) {
            packet.cancel();
            return;
        }
        reader.cancel(packet);
    }

    @Override
    public SendPacket takePacket() {
        SendPacket packet = queue.poll();
        if (packet == null) {
            return null;
        }

        if (packet.isCanceled()) {
            // canceled, no need to send
            return takePacket();
        }
        return packet;
    }

    @Override
    public void completedPacket(SendPacket packet, boolean isSucceed) {
        CloseUtils.close(packet);
    }

    /**
     * request network to send data
     */
    private void requestSend() {
        synchronized (isSending) {
            if (isSending.get() || isClosed.get()) {
                return;
            }
            // let reader to take a packet from dispatcher
            // return true means there is data to be sent
            if (reader.requestTakePacket()) {
                // if packet send is not canceled then request send
                // by here, the first frame has been already prepared as not canceled
                try {
                    boolean isSucceed = sender.postSendAsync();
                    if (isSucceed) {
                        isSending.set(true);
                    }
                } catch (IOException e) {
                    closeAndNotify();
                }
            }
        }
    }

    /**
     * will be invoked if network exception
     */
    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            // closure led by exception
            reader.close();
            // clear queue to prevent memory leek
            queue.clear();
            synchronized (isSending) {
                isSending.set(false);
            }
        }
    }


    @Override
    public IoArgs provideIoArgs() {
        return isClosed.get() ? null : reader.fillData();
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
        synchronized (isSending) {
            isSending.set(false);
        }
        // continue to request to send current data
        requestSend();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        synchronized (isSending) {
            isSending.set(false);
        }
        requestSend();
    }
}
