package net.qiujuer.lesson.sample.client;

import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImprovedClientTest {
    private static boolean done = false;

    public static void main(String[] args) throws IOException {
        File cachePath = Foo.getCacheDir("client/test");
        IoContext.setup().ioProvider(new IoSelectorProvider()).start();
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info == null) {
            return;
        }

        // current connection number
        int size = 0;
        final List<ImprovedTCPClient> tcpClients = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            try {
                ImprovedTCPClient tcpClient = ImprovedTCPClient.startWith(info, cachePath);
                if (tcpClient == null) {
                    throw new NullPointerException();
                }

                tcpClients.add(tcpClient);

                System.out.println("SUCCESS CONNECTION: " + (++size));
            } catch (IOException | NullPointerException e) {
                System.out.println("CONNECTION EXCEPTION");
                break;
            }
        }

        System.in.read();

        Runnable runnable = () -> {
            while (!done) {
                for (ImprovedTCPClient tcpClient : tcpClients) {
                    tcpClient.send("Hello~");
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        Thread child = new Thread(runnable);
        child.start();


        System.in.read();

        // wait for child thread to be finished
        done = true;
        try {
            child.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (ImprovedTCPClient tcpClient : tcpClients) {
            tcpClient.exit();
        }

        IoContext.close();
    }
}
