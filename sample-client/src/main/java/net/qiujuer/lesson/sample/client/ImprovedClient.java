package net.qiujuer.lesson.sample.client;


import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ImprovedClient {
    public static void main(String[] args) throws IOException {
        IoContext.setup().ioProvider(new IoSelectorProvider()).start();

        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info != null) {
            ImprovedTCPClient tcpClient = null;
            try {
                tcpClient = ImprovedTCPClient.startWith(info);
                if (tcpClient == null) {
                    return;
                }
                write(tcpClient);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != tcpClient) {
                    tcpClient.exit();
                }
            }
        }

        IoContext.close();
    }

    private static void write(ImprovedTCPClient tcpClient) throws IOException {
        // 构建键盘输入流
        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));

        do {
            // 键盘读取一行
            String str = input.readLine();
            // 发送到服务器
            tcpClient.send(str);

            if ("00bye00".equalsIgnoreCase(str)) {
                break;
            }
        } while (true);

    }
}
