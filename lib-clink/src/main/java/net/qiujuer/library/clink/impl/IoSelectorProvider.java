package net.qiujuer.library.clink.impl;

import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IoSelectorProvider implements IoProvider {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    // if in the middle of registration
    private final AtomicBoolean inRegInputLocker = new AtomicBoolean(false);
    private final AtomicBoolean inRegOutputLocker = new AtomicBoolean(false);


    private final Selector readSelector;
    private final Selector writeSelector;

    private final HashMap<SelectionKey, Runnable> inputCallbackMap = new HashMap<>();
    private final HashMap<SelectionKey, Runnable> outputCallbackMap = new HashMap<>();

    private final ExecutorService inputHandlePool;
    private final ExecutorService outputHandlePool;

    public IoSelectorProvider() throws IOException {
        readSelector = Selector.open();
        writeSelector = Selector.open();

        inputHandlePool = Executors.newFixedThreadPool(4, new IoThreadFactory("IoProvider-Input-Thread-"));
        outputHandlePool = Executors.newFixedThreadPool(4, new IoThreadFactory("IoProvider-Output-Thread-"));

        startRead();
        startWrite();
    }

    private void startRead() {
        Thread thread = new Thread("Clink IoSelectorProvider ReadSelector Thread") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        // It returns only after at least one channel is selected,
                        // this selector's {@link #wakeup wakeup} method is invoked
                        //  or the current thread is interrupted, whichever comes first.
                        if (readSelector.select() == 0) {
                            // this usually is due to wakeup has been called
                            waitSelection(inRegInputLocker); // should wait for registration finished if selector is being registered
                            continue;
                        }

                        Set<SelectionKey> selectionKeys = readSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                // send event to a thread pool to handle
                                handleSelection(selectionKey, SelectionKey.OP_READ, inputCallbackMap, inputHandlePool);
                            }
                        }
                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }

        };

        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }


    private void startWrite() {
        Thread thread = new Thread("Clink IoSelectorProvider WriteSelector Thread") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        // It returns only after at least one channel is selected,
                        // this selector's {@link #wakeup wakeup} method is invoked
                        //  or the current thread is interrupted, whichever comes first.
                        if (writeSelector.select() == 0) {
                            // this usually is due to wakeup has been called
                            waitSelection(inRegOutputLocker); // should wait for registration finished if selector is being registered
                            continue;
                        }

                        Set<SelectionKey> selectionKeys = writeSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                // send event to a thread pool to handle
                                handleSelection(selectionKey, SelectionKey.OP_WRITE, outputCallbackMap, outputHandlePool);
                            }
                        }
                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }


    @Override
    public boolean registerInput(SocketChannel channel, HandleInputCallback callback) {
        return registerSelection(channel, readSelector, SelectionKey.OP_READ, inRegInputLocker, inputCallbackMap, callback) != null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleOutputCallback callback) {
        return registerSelection(channel, writeSelector, SelectionKey.OP_WRITE, inRegOutputLocker, outputCallbackMap, callback) != null;
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel,readSelector,inputCallbackMap);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel,writeSelector,outputCallbackMap);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();

            inputCallbackMap.clear();
            outputCallbackMap.clear();

            readSelector.wakeup();
            writeSelector.wakeup();

            CloseUtils.close(readSelector, writeSelector);
        }
    }


    private static void waitSelection(final AtomicBoolean locker) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            if (locker.get()) {
                // selector is under registration
                try {
                    locker.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * register selector to socket channel, every channel should has a selectionkey given by selector
     * @param channel
     * @param selector
     * @param registerOps
     * @param locker
     * @param map
     * @param runnable
     * @return
     */
    private static SelectionKey registerSelection(SocketChannel channel, Selector selector,
                                                  int registerOps, AtomicBoolean locker,
                                                  HashMap<SelectionKey, Runnable> map, Runnable runnable) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            // set locked status
            locker.set(true);

            try {
                // 唤醒当前的selector，让selector不处于select()状态
                selector.wakeup();

                SelectionKey key = null;
                if (channel.isRegistered()) {
                    // search if channel has already be registered
                    key = channel.keyFor(selector);
                    if (key != null) {
                        // should continue to listen as could be erased by previous selection handling
                        key.interestOps(key.interestOps() | registerOps);
                    }
                }

                if (key == null) {
                    // register selector and get key
                    key = channel.register(selector, registerOps);
                    // register callback
                    map.put(key, runnable);
                }

                return key;
            } catch (ClosedChannelException e) {
                return null;
            } finally {
                // release lock
                locker.set(false);
                try {
                    // Wakes up a single thread that is waiting on this object's monitor
                    locker.notify();
                } catch (Exception ignored) {
                    // lock
                }
            }
        }
    }

    private static void unRegisterSelection(SocketChannel channel, Selector selector,
                                            Map<SelectionKey, Runnable> map) {
        if (channel.isRegistered()) {
            SelectionKey key = channel.keyFor(selector);
            if (key != null) {
                // 取消监听的方法
                key.cancel();
                map.remove(key);
                //  to make sure that the thread that has called the select() method which is blocked,
                // can be made to leave the select() method, even if no channels are yet ready.
                // continue to next select as one selection key has been removed
                selector.wakeup();
            }
        }
    }


    private void handleSelection(SelectionKey key, int keyOps,
                                 HashMap<SelectionKey, Runnable> map,
                                 ExecutorService pool) {
        // 重点
        // 取消继续对keyOps的监听
        // OP_READ = 0x00000001, OP_WRITE = Ox00000100
        // erase keyops by doing AND NOT operation
        key.interestOps(key.interestOps() & ~keyOps);
        Runnable runnable = null;
        try {
            runnable = map.get(key);
        } catch (Exception ignored) {
        }

        if (runnable != null && !inputHandlePool.isShutdown()) {
            // async call
            pool.execute(runnable);
        }
    }

    /**
     * The default thread factory
     */
    static class IoThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        IoThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            this.group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
