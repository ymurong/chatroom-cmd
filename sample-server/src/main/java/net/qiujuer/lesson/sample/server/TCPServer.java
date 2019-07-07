package net.qiujuer.lesson.sample.server;

import net.qiujuer.lesson.sample.server.handle.ClientHandler;
import net.qiujuer.lesson.sample.server.handle.ImprovedNioClientHandler;
import net.qiujuer.lesson.sample.server.handle.NioClientHandler;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPServer implements ClientHandler.ClientHandlerCallback, NioClientHandler.ClientHandlerCallback, ImprovedNioClientHandler.ClientHandlerCallback {
    private final int port;
    private ClientListener listener;
    private NioClientListener nioClientListener;
    private ImprovedNioClientListener improvedNioClientListener;
    private List<ClientHandler> clientHandlerList = new ArrayList<>();
    private List<NioClientHandler> nioClientHandlerList = new ArrayList<>();
    private List<ImprovedNioClientHandler> improvedNioClientHandlerList = new ArrayList<>();

    private final ExecutorService forwardingThreadPoolExecutor;
    private Selector selector;
    private ServerSocketChannel server;

    public TCPServer(int port) {
        this.port = port;
        this.forwardingThreadPoolExecutor = Executors.newCachedThreadPool();
    }

    public boolean start() {
        try {
            ClientListener listener = new ClientListener(port);
            listener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean startnio() {
        try {
            // Opens a selector.
            selector = Selector.open();
            ServerSocketChannel server = ServerSocketChannel.open();
            // set to non-blocking
            server.configureBlocking(false);
            // bind to local port
            server.socket().bind(new InetSocketAddress(port));
            // register arrival of client connection
            // Registers this channel with the given selector, returning a selection key.
            server.register(selector, SelectionKey.OP_ACCEPT);

            this.server = server;

            System.out.println("服务器信息：" + server.getLocalAddress().toString());

            // start client listener
            NioClientListener nioClientListener = this.nioClientListener = new NioClientListener();
            nioClientListener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean startimprovednio() {
        try {
            // Opens a selector.
            selector = Selector.open();
            ServerSocketChannel server = ServerSocketChannel.open();
            // set to non-blocking
            server.configureBlocking(false);
            // bind to local port
            server.socket().bind(new InetSocketAddress(port));
            // register arrival of client connection
            // Registers this channel with the given selector, returning a selection key.
            server.register(selector, SelectionKey.OP_ACCEPT);

            this.server = server;

            System.out.println("服务器信息：" + server.getLocalAddress().toString());

            // start client listener
            ImprovedNioClientListener improvedNioClientListener = this.improvedNioClientListener = new ImprovedNioClientListener();
            improvedNioClientListener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop() {
        if (improvedNioClientListener != null) {
            nioClientListener.exit();
        }

        CloseUtils.close(server);
        CloseUtils.close(selector);

        synchronized (TCPServer.this) {
            for (ClientHandler clientHandler : clientHandlerList) {
                clientHandler.exit();
            }
            clientHandlerList.clear();
        }
        forwardingThreadPoolExecutor.shutdownNow();
    }

    public synchronized void broadcast(String str) {
        for (ClientHandler clientHandler : clientHandlerList) {
            forwardingThreadPoolExecutor.execute(() -> clientHandler.sendSync(str));
        }
    }

    public synchronized void niobroadcast(String str) {
        for (NioClientHandler nioClientHandler : nioClientHandlerList) {
            forwardingThreadPoolExecutor.execute(() -> nioClientHandler.send(str));
        }
    }

    public synchronized void improvedNiobroadcast(String str) {
        for (ImprovedNioClientHandler improvedNioClientHandler : improvedNioClientHandlerList) {
            forwardingThreadPoolExecutor.execute(() -> improvedNioClientHandler.send(str));
        }
    }

    @Override
    public synchronized void onSelfClosed(ClientHandler handler) {
        clientHandlerList.remove(handler);
    }

    @Override
    public void onNewMessageArrived(ClientHandler handler, String msg) {
        // print to screen
        System.out.println("Received-" + handler.getClientInfo() + ":" + msg);
        // do not block otherwise it will block next messages, so need async operation here
        forwardingThreadPoolExecutor.execute(() -> {
            synchronized (TCPServer.this) {
                for (ClientHandler clientHandler : clientHandlerList) {
                    if (clientHandler.equals(handler)) {
                        // overlook itself
                        continue;
                    }
                    forwardingThreadPoolExecutor.execute(() -> {
                        clientHandler.sendSync(msg);
                    });
                }
            }

        });
    }

    @Override
    public void onSelfClosed(NioClientHandler handler) {
        nioClientHandlerList.remove(handler);
    }

    @Override
    public void onNewMessageArrived(NioClientHandler handler, String msg) {
        // print to screen
        System.out.println("Received-" + handler.getClientInfo() + ":" + msg);
        // do not block otherwise it will block next messages, so need async operation here
        forwardingThreadPoolExecutor.execute(() -> {
            synchronized (TCPServer.this) {
                for (NioClientHandler nioClientHandler : nioClientHandlerList) {
                    if (nioClientHandler.equals(handler)) {
                        // overlook itself
                        continue;
                    }
                    nioClientHandler.send(msg);
                }
            }
        });
    }

    @Override
    public void onSelfClosed(ImprovedNioClientHandler handler) {
        improvedNioClientHandlerList.remove(handler);
    }

    @Override
    public void onNewMessageArrived(ImprovedNioClientHandler handler, String msg) {
        // do not block otherwise it will block next messages, so need async operation here
        forwardingThreadPoolExecutor.execute(() -> {
            synchronized (TCPServer.this) {
                for (ImprovedNioClientHandler nioClientHandler : improvedNioClientHandlerList) {
                    if (nioClientHandler.equals(handler)) {
                        // overlook itself
                        continue;
                    }
                    nioClientHandler.send(msg);
                }
            }
        });
    }

    private class ClientListener extends Thread {
        private ServerSocket server;
        private boolean done = false;

        private ClientListener(int port) throws IOException {
            server = new ServerSocket(port);
            System.out.println("服务器信息：" + server.getInetAddress() + " P:" + server.getLocalPort());
        }

        @Override
        public void run() {
            super.run();

            System.out.println("服务器准备就绪～");
            // 等待客户端连接
            do {
                // 得到客户端
                Socket client;
                try {
                    client = server.accept();
                } catch (IOException e) {
                    continue;
                }
                try {
                    // 客户端构建异步线程
                    ClientHandler clientHandler = new ClientHandler(client, TCPServer.this);
                    // 读取数据并打印
                    clientHandler.readToPrint();
                    synchronized (TCPServer.this) {
                        clientHandlerList.add(clientHandler);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("客户端连接异常：" + e.getMessage());
                }
            } while (!done);

            System.out.println("服务器已关闭！");
        }

        void exit() {
            done = true;
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private class NioClientListener extends Thread {
        private boolean done = false;

        @Override
        public void run() {
            super.run();

            Selector selector = TCPServer.this.selector;
            System.out.println("服务器准备就绪～");

            // waiting for client connection
            do {
                // get client
                try {
                    //Selects a set of keys whose corresponding channels are ready for I/O
                    //operations.
                    if (selector.select() == 0) {
                        if (done) {
                            break;
                        }
                        continue;
                    }

                    // Returns this selector's selected-key set.
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        if (done) {
                            break;
                        }
                        SelectionKey key = iterator.next();
                        // Removes from the underlying collection the last element returned
                        // by this iterator (optional operation).
                        iterator.remove();

                        // check status key is what we want
                        // client arrival
                        if (key.isAcceptable()) {
                            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                            // get client connection in a non-blocking status
                            SocketChannel socketChannel = serverSocketChannel.accept();

                            try {
                                // client construct async thread
                                NioClientHandler nioClientHandler = new NioClientHandler(socketChannel, TCPServer.this);
                                // read data and print
                                nioClientHandler.readToPrint(); // one thread to loop reading
                                // add sync handling
                                synchronized (TCPServer.this) {
                                    nioClientHandlerList.add(nioClientHandler);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("Client connection exception" + e.getMessage());
                            }
                        }
                    }
                } catch (IOException e) {
                    continue;
                }
            } while (!done);

            System.out.println("服务器已关闭！");
        }
        void exit() {
            done = true;
            // wakeup current blocking
            selector.wakeup();
        }
    }

    private class ImprovedNioClientListener extends Thread {
        private boolean done = false;

        @Override
        public void run() {
            super.run();

            Selector selector = TCPServer.this.selector;
            System.out.println("服务器准备就绪～");

            // waiting for client connection
            do {
                // get client
                try {
                    //Selects a set of keys whose corresponding channels are ready for I/O
                    //operations.
                    if (selector.select() == 0) {
                        if (done) {
                            break;
                        }
                        continue;
                    }

                    // Returns this selector's selected-key set.
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        if (done) {
                            break;
                        }
                        SelectionKey key = iterator.next();
                        // Removes from the underlying collection the last element returned
                        // by this iterator (optional operation).
                        iterator.remove();

                        // check status key is what we want
                        // client arrival
                        if (key.isAcceptable()) {
                            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                            // get client connection in a non-blocking status
                            SocketChannel socketChannel = serverSocketChannel.accept();

                            try {
                                // client construct async thread -> start reading messages
                                ImprovedNioClientHandler improvedNioClientHandler = new ImprovedNioClientHandler(socketChannel, TCPServer.this);
                                // add sync handling
                                synchronized (TCPServer.this) {
                                    improvedNioClientHandlerList.add(improvedNioClientHandler);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("Client connection exception" + e.getMessage());
                            }
                        }
                    }
                } catch (IOException e) {
                    continue;
                }
            } while (!done);

            System.out.println("服务器已关闭！");
        }

        void exit() {
            done = true;
            // wakeup current blocking
            selector.wakeup();
        }
    }


}

