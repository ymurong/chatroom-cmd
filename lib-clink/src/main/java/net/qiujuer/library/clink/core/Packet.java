package net.qiujuer.library.clink.core;

import java.io.Closeable;

public abstract class Packet implements Closeable {
    protected byte type;
    protected int length;

    public byte type() {
        return type;
    }

    public int length() {
        return length;
    }

}
