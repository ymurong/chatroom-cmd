package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.*;
import net.qiujuer.library.clink.utils.CloseUtils;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncReceiveDispatcher implements ReceiveDispatcher, IoArgs.IoArgsEventProcessor {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Receiver receiver;
    private final ReceiveDispatcher.ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    // current packet
    private ReceivePacket<?> packetTemp;

    private WritableByteChannel packetChannel;
    // current receiving packet length
    private long total;
    // current receiving packet position
    private long position;

    public AsyncReceiveDispatcher(Receiver receiver, ReceiveDispatcher.ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(this);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }


    @Override
    public void stop() {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            completePacket(false);
        }
    }

    private void registerReceive() {
        try {
            receiver.postReceiveAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }


    private void assemblePacket(IoArgs args) {
        // args could contains incomplete message (first part of the message)
        // we use a current packet to make sure wrap the full data that could arrive later
        if (packetTemp == null) {
            // no existent current packet -> a new packet to receive
            // read the message length
            int length = args.readLength();
            // init a receive packet with the exact length
            packetTemp = new StringReceivePacket(length);
            packetChannel = Channels.newChannel(packetTemp.open());
            // init a new buffer with the exact length
            total = length;
            position = 0;
        }
        try {
            // transfer data received from socket channel to our dispatcher buffer
            int count = args.writeTo(packetChannel);
            position += count;

            // check if a packet has been fully received
            if (position == total) {
                completePacket(true);
            }
        } catch (IOException e) {
            e.printStackTrace();
            completePacket(false);
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    private void completePacket(boolean isSucceed) {
        ReceivePacket packet = this.packetTemp;
        CloseUtils.close(packet);
        packetTemp = null;

        // wrap the packet to send
        WritableByteChannel channel = this.packetChannel;
        CloseUtils.close(channel);
        packetChannel = null;

        if (packet != null) {
            callback.onReceivePacketCompleted(packet);
        }
    }


    @Override
    public IoArgs provideIoArgs() {
        IoArgs args = ioArgs;

        int receiveSize;
        if (packetTemp == null) {
            // first to receive the message length
            receiveSize = 4;
        } else {
            // receive the rest of the message
            // to prevent Ioargs buffer from being overflowed
            receiveSize = (int) Math.min(total - position, args.capacity());
        }
        // set the size of data to receive to prevent sticky message
        args.limit(receiveSize);

        return args;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        assemblePacket(args);
        registerReceive();
    }
}
