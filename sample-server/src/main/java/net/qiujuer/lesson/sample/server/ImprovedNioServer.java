package net.qiujuer.lesson.sample.server;

import net.qiujuer.lesson.sample.foo.constants.TCPConstants;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ImprovedNioServer {
    public static void main(String[] args) throws IOException {
        // setup ioprovider(thread pool) and start read and write selector, waiting to be registed by new socket channel
        IoContext.setup().ioProvider(new IoSelectorProvider()).start();
        TCPServer tcpServer = new TCPServer(TCPConstants.PORT_SERVER);
        // server start to accept connections
        // every acceptable channel will be delegated to a new ImprovedNioClientHandler which used the same provider
        boolean isSucceed = tcpServer.startimprovednio();
        if (!isSucceed) {
            System.out.println("Start TCP server failed!");
            return;
        }

        UDPProvider.start(TCPConstants.PORT_SERVER);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String str;
        do {
            str = bufferedReader.readLine();
            tcpServer.improvedNiobroadcast(str);
        } while (!"00bye00".equalsIgnoreCase(str));

        UDPProvider.stop();
        tcpServer.stop();
        IoContext.close();
    }
}
