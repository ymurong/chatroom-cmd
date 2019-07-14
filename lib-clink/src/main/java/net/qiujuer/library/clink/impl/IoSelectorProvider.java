package net.qiujuer.library.clink.impl;

import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
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

        inputHandlePool = Executors.newFixedThreadPool(20, new IoThreadFactory("IoProvider-Input-Thread-"));
        outputHandlePool = Executors.newFixedThreadPool(20, new IoThreadFactory("IoProvider-Output-Thread-"));

        startRead();
        startWrite();
    }

    private void startRead() {
        Thread thread = new SelectThread("Clink IoSelectorProvider ReadSelector Thread",
                isClosed, inRegInputLocker, readSelector,
                inputCallbackMap, inputHandlePool,
                SelectionKey.OP_READ);
        thread.start();
    }


    private void startWrite() {
        Thread thread = new SelectThread("Clink IoSelectorProvider WriteSelector Thread",
                isClosed, inRegOutputLocker, writeSelector,
                outputCallbackMap, outputHandlePool,
                SelectionKey.OP_WRITE);
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
        unRegisterSelection(channel, readSelector, inputCallbackMap, inRegInputLocker);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallbackMap, inRegOutputLocker);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();

            inputCallbackMap.clear();
            outputCallbackMap.clear();

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
     *
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
            } catch (ClosedChannelException
                    | CancelledKeyException
                    | ClosedSelectorException e) {
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
                                            Map<SelectionKey, Runnable> map,
                                            AtomicBoolean locker) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            locker.set(true);
            selector.wakeup();
            try {
                if (channel.isRegistered()) {
                    SelectionKey key = channel.keyFor(selector);
                    if (key != null) {
                        // 取消监听的方法
                        key.cancel();
                        map.remove(key);
                    }
                }
            } finally {
                locker.set(false);
                try {
                    locker.notifyAll();
                } catch (Exception ignored) {
                }
            }
        }
    }


    private static void handleSelection(SelectionKey key, int keyOps,
                                        HashMap<SelectionKey, Runnable> map,
                                        ExecutorService pool, AtomicBoolean locker) {

        synchronized (locker) {
            try {
                // 重点
                // 取消继续对keyOps的监听
                // OP_READ = 0x00000001, OP_WRITE = Ox00000100
                // erase keyops by doing AND NOT operation
                key.interestOps(key.readyOps() & ~keyOps);
            } catch (CancelledKeyException e) {
                return;
            }
        }

        Runnable runnable = null;
        try {
            runnable = map.get(key);
        } catch (Exception ignored) {

        }

        if (runnable != null && !pool.isShutdown()) {
            // async call
            pool.execute(runnable);
        }
    }


    static class SelectThread extends Thread {
        private final AtomicBoolean isClosed;
        private final AtomicBoolean locker;
        private final Selector selector;
        private final HashMap<SelectionKey, Runnable> callMap;
        private final ExecutorService pool;
        private final int keyOps;

        SelectThread(String name,
                     AtomicBoolean isClosed, AtomicBoolean locker,
                     Selector selector,
                     HashMap<SelectionKey, Runnable> callMap,
                     ExecutorService pool, int keyOps) {
            super(name);
            this.isClosed = isClosed;
            this.locker = locker;
            this.selector = selector;
            this.callMap = callMap;
            this.pool = pool;
            this.keyOps = keyOps;
            this.setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            super.run();
            AtomicBoolean locker = this.locker;
            AtomicBoolean isClosed = this.isClosed;
            Selector selector = this.selector;
            HashMap<SelectionKey, Runnable> callMap = this.callMap;
            ExecutorService pool = this.pool;
            int keyOps = this.keyOps;


            while (!isClosed.get()) {
                try {
                    // It returns only after at least one channel is selected,
                    // this selector's {@link #wakeup wakeup} method is invoked
                    //  or the current thread is interrupted, whichever comes first.
                    if (selector.select() == 0) {
                        // this usually is due to wakeup has been called
                        // should wait for registration finished if selector is being registered
                        waitSelection(locker);
                        continue;
                    } else if (locker.get()) {
                        waitSelection(locker);
                    }

                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        if (selectionKey.isValid()) {
                            // send event to a thread pool to handle
                            handleSelection(selectionKey,
                                    keyOps, callMap, pool, locker);
                        }
                        iterator.remove();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClosedSelectorException ignored) {
                    break;
                }
            }
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
