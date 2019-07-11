package net.qiujuer.lesson.sample.server.handle;


import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ImprovedNioClientHandler extends Connector {
    private final File cachePath;
    private final ClientHandlerCallback clientHandlerCallback;
    private final String clientInfo;

    // clientHandlerCallback <= TcpServer
    public ImprovedNioClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback, File cachePath) throws IOException {
        this.cachePath = cachePath;
        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        System.out.println("新客户端连接：" + clientInfo);
        setup(socketChannel);
    }


    public void exit() {
        CloseUtils.close(this);
        System.out.println("客户端已退出：" + clientInfo);
    }

    @Override
    public void onChannelClosed(SocketChannel socketChannel) {
        super.onChannelClosed(socketChannel);
        exitBySelf();
    }

    @Override
    protected File createNewReceiveFile() {
        return Foo.createRandomTemp(cachePath);
    }

    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    @Override
    protected void onReceivePacket(ReceivePacket packet) {
        super.onReceivePacket(packet);
        if (packet.type() == Packet.TYPE_MEMORY_STRING) {
            String string = (String) packet.entity();
            System.out.println(this.getClass().getCanonicalName() + ": " + key.toString() + ":" + string);
            clientHandlerCallback.onNewMessageArrived(this, string);
        }
    }

    public interface ClientHandlerCallback {
        // self closure
        void onSelfClosed(ImprovedNioClientHandler handler);

        // notify new message arrive
        // transfer messages to other clients
        void onNewMessageArrived(ImprovedNioClientHandler handler, String msg);
    }
}
