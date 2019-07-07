package net.qiujuer.library.clink.core;

import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.box.StringSendPacket;
import net.qiujuer.library.clink.impl.SocketChannelAdapter;
import net.qiujuer.library.clink.impl.async.AsyncReceiveDispatcher;
import net.qiujuer.library.clink.impl.async.AsyncSendDispatcher;

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
    private SendDispatcher sendDispatcher;
    private ReceiveDispatcher receiveDispatcher;

    public void setup(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;

        IoContext ioContext = IoContext.get();
        // adapter will use the same shared IoProvider for all acceptable channels    adapter 1 <-> 1 connector 1 <-> n Provider
        this.adapter = new SocketChannelAdapter(channel, ioContext.getIoProvider(), this);

        this.sender = adapter;
        this.receiver = adapter;

        sendDispatcher = new AsyncSendDispatcher(sender);
        receiveDispatcher = new AsyncReceiveDispatcher(receiver,receivePacketCallback);

        // 启动接收
        receiveDispatcher.start();
    }

    public void send(String msg) {
        SendPacket packet = new StringSendPacket(msg);
        sendDispatcher.send(packet);
    }


    @Override
    public void close() throws IOException {
        receiveDispatcher.close();
        sendDispatcher.close();
        sender.close();
        receiver.close();
        channel.close();
    }

    @Override
    public void onChannelClosed(SocketChannel socketChannel) {

    }

    protected void onReceiveNewMessage(String str) {
        System.out.println("[clink] " + key.toString() + ":" + str);
    }

    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback = new ReceiveDispatcher.ReceivePacketCallback() {
        @Override
        public void onReceivePacketCompleted(ReceivePacket packet) {
            if(packet instanceof StringReceivePacket){
                String msg = ((StringReceivePacket)packet).string();
                onReceiveNewMessage(msg);
            }
        }
    };
}
