package net.qiujuer.library.clink.core;

import java.io.Closeable;

public interface SendDispatcher extends Closeable {

    void send(SendPacket packet);

    void cancel(SendPacket packet);
}
