package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;

public abstract class Packet<T extends Closeable> implements Closeable {
    private T stream;
    protected long length;
    protected byte type;

    public byte type() {
        return type;
    }

    public long length() {
        return length;
    }

    public final T open() {
        if (stream == null) {
            stream = createStream();
        }
        return stream;
    }

    protected abstract T createStream();


    @Override
    public final void close() throws IOException {
        if (stream != null) {
            closeStream(stream);
            stream = null;
        }
    }

    protected void closeStream(T stream) throws IOException {
        stream.close();
    }


}
