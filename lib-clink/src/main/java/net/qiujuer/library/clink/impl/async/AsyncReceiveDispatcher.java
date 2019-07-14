package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.core.*;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncReceiveDispatcher implements ReceiveDispatcher, IoArgs.IoArgsEventProcessor, AsyncPacketWriter.PacketProvider {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Receiver receiver;
    private final ReceiveDispatcher.ReceivePacketCallback callback;

    private final AsyncPacketWriter writer = new AsyncPacketWriter(this);

    public AsyncReceiveDispatcher(Receiver receiver, ReceiveDispatcher.ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(this);
        this.callback = callback;
    }

    @Override
    public void start() {
        // register readable event
        registerReceive();
    }


    @Override
    public void stop() {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            writer.close();
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    private void registerReceive() {
        try {
            receiver.postReceiveAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }


    @Override
    public IoArgs provideIoArgs() {
        return writer.takeIoArgs();
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        if (isClosed.get()) {
            return;
        }
        do {
            writer.consumeIoArgs(args);
        } while (args.remained() && !isClosed.get());

        // register to read more data from channel
        registerReceive();
    }

    @Override
    public ReceivePacket takePacket(byte type, long length, byte[] headerInfo) {
        return callback.onArrivedNewPacket(type, length);
    }

    @Override
    public void completedPacket(ReceivePacket packet, boolean isSucceed) {
        CloseUtils.close(packet);
        callback.onReceivePacketCompleted(packet);
    }
}
