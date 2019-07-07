package net.qiujuer.library.clink.core;

import java.io.Closeable;

/**
 * wrap received data (one or more Ioargs) into packet
 *
 */
public interface ReceiveDispatcher extends Closeable {

    void start();

    void stop();

    interface ReceivePacketCallback{
        void onReceivePacketCompleted(ReceivePacket packet);
    }
}
