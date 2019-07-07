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

    private IoArgs receiveArgsTemp;

    public SocketChannelAdapter(SocketChannel socketChannel, IoProvider ioProvider, OnChannelStatusChangedListener listener) throws IOException {
        this.socketChannel = socketChannel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        socketChannel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventListener listener) {
        receiveIoEventListener = listener;
    }

    @Override
    public boolean receiveAsync(IoArgs args) throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed");
        }
        receiveArgsTemp = args;
        // register channel to selector or reinit its selectionkey
        return ioProvider.registerInput(socketChannel, inputCallback);
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
     * this is a runnable which will be called when socket channel is readable
     */
    private final IoProvider.HandleInputCallback inputCallback = new IoProvider.HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if (isClosed.get()) {
                return;
            }

            IoArgs args = receiveArgsTemp;
            IoArgs.IoArgsEventListener listener = SocketChannelAdapter.this.receiveIoEventListener;
            listener.onStarted(args);

            try {
                // readFrom operation
                if (args.readFrom(socketChannel) > 0) {
                    // complete readFrom callback
                    listener.onCompleted(args);
                } else {
                    throw new IOException("Cannnot readFrom any data from current socketChannel");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    /**
     * this is a runnable which will be called when socket channel is writable
     */

    private final IoProvider.HandleOutputCallback outputCallback = new IoProvider.HandleOutputCallback() {
        @Override
        protected void canProviderOutput(Object attach) {
            if (isClosed.get()) {
                return;
            }
            IoArgs args = getAttach();
            IoArgs.IoArgsEventListener listener = sendIoEventListener;

            listener.onStarted(args);

            try {
                // writeTo operation
                if (args.writeTo(socketChannel) > 0) {
                    // complete writeTo callback
                    listener.onCompleted(args);
                } else {
                    throw new IOException("Cannnot write any data to current socketChannel");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    public interface OnChannelStatusChangedListener {
        void onChannelClosed(SocketChannel socketChannel);
    }

}
