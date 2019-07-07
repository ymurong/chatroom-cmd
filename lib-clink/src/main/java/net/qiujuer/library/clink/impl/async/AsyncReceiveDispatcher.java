package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.*;
import net.qiujuer.library.clink.utils.CloseUtils;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncReceiveDispatcher implements ReceiveDispatcher {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Receiver receiver;
    private final ReceiveDispatcher.ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    // current packet
    private ReceivePacket packetTemp;
    private byte[] buffer;

    // current receiving packet length
    private int total;
    // current receiving packet position
    private int position;

    public AsyncReceiveDispatcher(Receiver receiver, ReceiveDispatcher.ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(ioArgsEventListener);
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
            ReceivePacket packet = this.packetTemp;
            if (packet != null) {
                packetTemp = null;
                CloseUtils.close(packet);
            }
        }
    }

    private void registerReceive() {
        try {
            receiver.receiveAsync(ioArgs);
        } catch (IOException e) {
            closeAndNotify();
        }
    }


    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    private void completePacket() {
        ReceivePacket packet = this.packetTemp;
        // wrap the packet to send
        CloseUtils.close(packet);
        callback.onReceivePacketCompleted(packet);
    }

    /**
     * this is the Ioargs listener which will be invoked when start or finish reading from socket channel
     */
    private final IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {
            int receiveSize;
            if (packetTemp == null) {
                // first to receive the message length
                receiveSize = 4;
            } else {
                // receive the rest of the message
                // to prevent Ioargs buffer from being overflowed
                receiveSize = Math.min(total - position, args.capacity());
            }
            // set the size of data to receive to prevent sticky message
            args.limit(receiveSize);
        }

        @Override
        public void onCompleted(IoArgs args) {
            assemblePacket(args);
            // continue to receive next data
            registerReceive();
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
                // init a new buffer with the exact length
                buffer = new byte[length];
                total = length;
                position = 0;
            }
            // transfer data received from socket channel to our dispatcher buffer
            int count = args.writeTo(buffer, 0);
            // some times you will have zero as buffer args has already been read previously <= args.readLength();
            if (count > 0) {
                packetTemp.save(buffer, count);
                // register current packet position to judge whether message has been completely received
                position += count;

                if (position == total) {
                    //transfer complete packet to outside invoker
                    completePacket();
                    //removed current packet as it has been fully received by outside invoker
                    packetTemp = null;
                }
            }
        }
    };


}
