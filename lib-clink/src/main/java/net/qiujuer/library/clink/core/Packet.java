package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;

public abstract class Packet<Stream extends Closeable> implements Closeable {
    // BYTES Type
    public static final byte TYPE_MEMORY_BYTES = 1;
    // String Type
    public static final byte TYPE_MEMORY_STRING = 2;
    // File Type
    public static final byte TYPE_STREAM_FILE = 3;
    // Long connection stream Type
    public static final byte TYPE_STREAM_DIRECT = 4;

    private Stream stream;
    protected long length;

    public long length() {
        return length;
    }

    public final Stream open() {
        if (stream == null) {
            stream = createStream();
        }
        return stream;
    }

    /**
     * type get through method
     * <p>
     * {@link #TYPE_MEMORY_BYTES}
     * {@link #TYPE_MEMORY_STRING}
     * {@link #TYPE_STREAM_FILE}
     * {@link #TYPE_STREAM_DIRECT}
     *
     * @return Type
     */
    public abstract byte type();

    protected abstract Stream createStream();


    @Override
    public final void close() throws IOException {
        if (stream != null) {
            closeStream(stream);
            stream = null;
        }
    }

    protected void closeStream(Stream stream) throws IOException {
        stream.close();
    }

    /**
     * supplementary header info
     *
     * @return byte array，maximum 255
     */
    public byte[] headerInfo() {
        return null;
    }

}
