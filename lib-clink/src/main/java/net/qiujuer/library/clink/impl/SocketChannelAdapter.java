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

    private IoArgs.IoArgsEventProcessor receiveIoEventProcessor;
    private IoArgs.IoArgsEventProcessor sendIoEventProcessor;

    public SocketChannelAdapter(SocketChannel socketChannel, IoProvider ioProvider, OnChannelStatusChangedListener listener) throws IOException {
        this.socketChannel = socketChannel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        socketChannel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventProcessor processor) {
        receiveIoEventProcessor = processor;
    }

    @Override
    public boolean postReceiveAsync() throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed");
        }

        inputCallback.checkAttachNull();
        // register channel to selector or reinit its selectionkey
        return ioProvider.registerInput(socketChannel, inputCallback);
    }

    @Override
    public void setSendListener(IoArgs.IoArgsEventProcessor processor) {
        sendIoEventProcessor = processor;
    }

    @Override
    public boolean postSendAsync() throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed");
        }
        outputCallback.checkAttachNull();
        return ioProvider.registerOutput(socketChannel, outputCallback);
    }


    /**
     * will be invoked by IOException of SocketChannelAdapter during reading or writing on socket channel
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            ioProvider.unRegisterInput(socketChannel);
            ioProvider.unRegisterOutput(socketChannel);
            CloseUtils.close(socketChannel);
            // callback when channel closed
            // notify connector in order to close resources
            listener.onChannelClosed(socketChannel);
        }
    }

    /**
     * this is a runnable which will be called when socket channel is readable
     */
    private final IoProvider.HandleProviderCallback inputCallback = new IoProvider.HandleProviderCallback() {
        @Override
        protected void onProviderIo(IoArgs args) {
            if (isClosed.get()) {
                return;
            }

            // this processor is set by AsyncSendDispatcher
            IoArgs.IoArgsEventProcessor processor = receiveIoEventProcessor;
            if (args == null) {
                // get a new consumable IoArgs
                args = processor.provideIoArgs();
            }

            try {
                if (args == null) {
                    processor.onConsumeFailed(null, new IOException("ProvideIoArgs is null."));
                } else {
                    int count = args.readFrom(socketChannel);
                    if (count == 0) {
                        System.out.println("Current write zero data");
                    }
                    if (args.remained()) {
                        // attach unconsumed args
                        attach = args;
                        // complete writeTo callback
                        ioProvider.registerInput(socketChannel, this);
                    } else {
                        attach = null;
                        // read fished callback
                        processor.onConsumeCompleted(args);
                    }
                }
            } catch (IOException ignored) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    /**
     * this is a runnable which will be called when socket channel is writable
     */

    private final IoProvider.HandleProviderCallback outputCallback = new IoProvider.HandleProviderCallback() {
        @Override
        protected void onProviderIo(IoArgs args) {
            if (isClosed.get()) {
                return;
            }

            IoArgs.IoArgsEventProcessor processor = sendIoEventProcessor;
            // get a consumable IoArgs via dispatcher (via reader currentFrame) and prepare next frame
            // will fill IoArgs via frame (its channel)
            if (args == null) {
                args = processor.provideIoArgs();
            }

            try {
                // writeTo operation to consume args
                if (args == null) {
                    processor.onConsumeFailed(null, new IOException("ProvideIoArgs is null."));
                } else {
                    int count = args.writeTo(socketChannel);
                    if (count == 0) {
                        System.out.println("Current write zero data!");
                    }

                    if (args.remained()) {
                        // attach unconsumed args
                        attach = args;
                        // complete writeTo callback
                        ioProvider.registerOutput(socketChannel, this);
                    } else {
                        attach = null;
                        // write finished callback
                        processor.onConsumeCompleted(args);
                    }
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
