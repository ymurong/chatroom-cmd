package net.qiujuer.lesson.sample.client;


import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

public class ImprovedTCPClient extends Connector {
    private final File cachePath;

    public ImprovedTCPClient(SocketChannel socketChannel, File cachePath) throws IOException {
        this.cachePath = cachePath;
        setup(socketChannel);
    }

    public void exit() {
        CloseUtils.close(this);
    }

    @Override
    public void onChannelClosed(SocketChannel socketChannel) {
        super.onChannelClosed(socketChannel);
        System.out.println("Connection has already been closed, cannot read data anymore...");
    }

    @Override
    protected File createNewReceiveFile() {
        return Foo.createRandomTemp(cachePath);
    }

    @Override
    protected void onReceivePacket(ReceivePacket packet) {
        super.onReceivePacket(packet);
        /*
        if (packet.type() == Packet.TYPE_MEMORY_STRING) {
            String string = (String) packet.entity();
            // System.out.println(key.toString() + ":" + string);
        }
        */
    }

    public static ImprovedTCPClient startWith(ServerInfo info, File cachePath) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();

        // 连接本地，端口2000；超时时间3000ms
        socketChannel.connect(new InetSocketAddress(Inet4Address.getByName(info.getAddress()), info.getPort()));

        System.out.println("已发起服务器连接，并进入后续流程～");
        System.out.println("客户端信息：" + socketChannel.getLocalAddress().toString());
        System.out.println("服务器信息：" + socketChannel.getRemoteAddress().toString());

        try {
            // 发送接收数据
            return new ImprovedTCPClient(socketChannel, cachePath);
        } catch (Exception e) {
            System.out.println("Connection Exception");
            CloseUtils.close(socketChannel);
        }
        return null;
    }

}
