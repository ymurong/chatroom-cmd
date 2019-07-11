package net.qiujuer.library.clink.core;

import java.io.Closeable;

/**
 * wrap received data (one or more Ioargs) into packet
 */
public interface ReceiveDispatcher extends Closeable {

    void start();

    void stop();

    interface ReceivePacketCallback {
        /**
         * when a new packet arrives for the first time, it will be invoked
         * @param type
         * @param length
         * @return
         */
        ReceivePacket<?, ?> onArrivedNewPacket(byte type, long length);

        /**
         * when we get the complete ReceivePacket, this callback will be invoked
         * @param packet
         */
        void onReceivePacketCompleted(ReceivePacket packet);
    }
}
