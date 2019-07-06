package net.qiujuer.library.clink.core;

import net.qiujuer.library.clink.impl.SocketChannelAdapter;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.UUID;

public class Connector implements Closeable, SocketChannelAdapter.OnChannelStatusChangedListener {
    private UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private Sender sender;
    private Receiver receiver;
    private SocketChannelAdapter adapter;

    public void setup(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;

        IoContext ioContext = IoContext.get();
        // adapter will use the same shared IoProvider for all acceptable channels    adapter 1 <-> 1 connector 1 <-> n Provider
        this.adapter = new SocketChannelAdapter(channel, ioContext.getIoProvider(), this);

        this.sender = adapter;
        this.receiver = adapter;

        readNextMessage();
    }

    private void readNextMessage() {
        if (receiver != null) {
            try {
                receiver.receiveAsync(echoReceiveListener);
            } catch (IOException e) {
                System.out.println("Error while receiving data" + e.getMessage());
            }
        }
    }


    @Override
    public void close() throws IOException {
        if (adapter != null) {
            adapter.close();
        }
    }

    @Override
    public void onChannelClosed(SocketChannel socketChannel) {

    }

    private IoArgs.IoArgsEventListener echoReceiveListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            // print
            onReceiveNewMessage(args.bufferString());
            // read next message
            readNextMessage();
        }
    };

    protected void onReceiveNewMessage(String str) {
        System.out.println("[clink] "+key.toString() + ":" + str);
    }
}
