package net.qiujuer.library.clink.impl;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelAdapter implements Sender, Receiver, Cloneable {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel socketChannel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangedListener listener;

    private IoArgs.IoArgsEventListener receiveIoEventListener;
    private IoArgs.IoArgsEventListener sendIoEventListener;


    public SocketChannelAdapter(SocketChannel socketChannel, IoProvider ioProvider, OnChannelStatusChangedListener listener) throws IOException {
        this.socketChannel = socketChannel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        socketChannel.configureBlocking(false);
    }

    @Override
    public boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed");
        }

        sendIoEventListener = listener;

        // put the data that is  going to be sent into callback
        outputCallback.setAttach(args);
        return ioProvider.registerOutput(socketChannel, outputCallback);
    }

    @Override
    public boolean receiveAsync(IoArgs.IoArgsEventListener listener) throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed");
        }

        receiveIoEventListener = listener;

        // register channel to selector or reinit its selectionkey
        return ioProvider.registerInput(socketChannel, inputCallback);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            ioProvider.unRegisterInput(socketChannel);
            ioProvider.unRegisterOutput(socketChannel);
            CloseUtils.close(socketChannel);
            // callback when channel closed
            listener.onChannelClosed(socketChannel);
        }
    }

    /**
     * this is a runnable
     */
    private final IoProvider.HandleInputCallback inputCallback = new IoProvider.HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if (isClosed.get()) {
                return;
            }

            IoArgs args = new IoArgs();
            IoArgs.IoArgsEventListener receiveIoEventListener = SocketChannelAdapter.this.receiveIoEventListener;
            receiveIoEventListener.onStarted(args);

            try {
                // read operation
                if (args.read(socketChannel) > 0 && receiveIoEventListener != null) {
                    // complete read callback
                    receiveIoEventListener.onCompleted(args);
                } else{
                    throw new IOException("Cannnot read any data from current socketChannel");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    private final IoProvider.HandleOutputCallback outputCallback = new IoProvider.HandleOutputCallback() {

        @Override
        protected void canProviderOutput(Object attach) {

        }
    };

    public interface OnChannelStatusChangedListener {
        void onChannelClosed(SocketChannel socketChannel);
    }

}
