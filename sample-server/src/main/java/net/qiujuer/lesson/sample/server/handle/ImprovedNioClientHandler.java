package net.qiujuer.lesson.sample.server.handle;


import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ImprovedNioClientHandler extends Connector {
    private final ClientHandlerCallback clientHandlerCallback;
    private final String clientInfo;

    // clientHandlerCallback <= TcpServer
    public ImprovedNioClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback) throws IOException {
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

    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    @Override
    protected void onReceiveNewMessage(String str) {
        super.onReceiveNewMessage(str);
        clientHandlerCallback.onNewMessageArrived(this,str);
    }

    public interface ClientHandlerCallback {
        // self closure
        void onSelfClosed(ImprovedNioClientHandler handler);

        // notify hen new message arrive
        void onNewMessageArrived(ImprovedNioClientHandler handler, String msg);
    }
}
