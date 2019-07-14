package net.qiujuer.library.clink.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class IoArgs {
    private int limit = 256;
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);

    public int readFrom(byte[] bytes, int offset, int count) {
        int size = Math.min(count, buffer.remaining());
        // This method transfers bytes into this buffer from the given source array.
        if (size <= 0) {
            return 0;
        }
        buffer.put(bytes, offset, size);
        return size;
    }

    /**
     * readFrom data from ReadableByteChannel into buffer
     *
     * @param bytes
     * @param offset
     * @return
     */
    public int readFrom(ReadableByteChannel channel) throws IOException {
        // startWriting(); // init buffer
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.read(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        // finishWriting(); // set buffer ready to be read
        return bytesProduced;
    }


    /**
     * readFrom data from bytes into buffer
     *
     * @param bytes
     * @param offset from which position where we take the data
     * @return
     */
    public int readFrom(byte[] bytes, int offset) {
        int size = Math.min(bytes.length - offset, buffer.remaining());
        // This method transfers bytes into this buffer from the given source array.
        buffer.put(bytes, offset, size);
        return size;
    }

    /**
     * write data to WritableByteChannel
     *
     * @param bytes
     * @param offset
     * @return
     */
    public int writeTo(WritableByteChannel channel) throws IOException {
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.write(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        return bytesProduced;
    }

    /**
     * writeTo data from buffer into bytes
     *
     * @param bytes
     * @param offset
     * @return
     */
    public int writeTo(byte[] bytes, int offset) {
        // to prevent bytes from being overflowed
        int size = Math.min(bytes.length - offset, buffer.remaining());
        // This method transfers bytes from this buffer into the given destination array.
        buffer.get(bytes, offset, size);
        return size;
    }

    /**
     * read data from SocketChannel
     *
     * @param channel
     * @return
     * @throws IOException
     */
    public int readFrom(SocketChannel channel) throws IOException {
        startWriting(); // init buffer
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.read(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        finishWriting(); // set buffer ready to be read
        return bytesProduced;
    }

    /**
     * write data to SocketChannel
     *
     * @param channel
     * @return
     * @throws IOException
     */
    public int writeTo(SocketChannel channel) throws IOException {
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.write(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        return bytesProduced;
    }

    /**
     * invoke before writing to buffer
     */
    public void startWriting() {
        buffer.clear();
        buffer.limit(limit);
    }

    /**
     * invoke after writing to buffer
     */
    public void finishWriting() {
        buffer.flip(); // position -> 0 limit -> length capacity -> fullsize
    }

    /**
     * set single writing limit
     *
     * @param limit
     */
    public void limit(int limit) {
        this.limit = Math.min(limit, buffer.capacity());
    }


    public int readLength() {
        return buffer.getInt();
    }

    public int capacity() {
        return buffer.capacity();
    }

    public boolean remained() {
        return buffer.remaining() > 0;
    }

    /**
     * @param size the size of data that need to be filled
     * @return the size of data filled
     */
    public int fillEmpty(int size) {
        int fillSize = Math.min(size, buffer.remaining());
        buffer.position(buffer.position() + fillSize);
        return fillSize;
    }

    public int setEmpty(int size) {
        int emptySize = Math.min(size, buffer.remaining());
        buffer.position(buffer.position() + emptySize);
        return emptySize;
    }


    /**
     * IoArgs provider/processor; data producer/consumer
     */
    public interface IoArgsEventProcessor {
        /**
         * provide a consumbale IoArgs
         *
         * @return IoArgs
         */
        IoArgs provideIoArgs();

        /**
         * callback when consumption failed
         *
         * @param args IoArgs
         * @param e    Exception information
         */
        void onConsumeFailed(IoArgs args, Exception e);

        /**
         * consumption success
         *
         * @param args IoArgs
         */
        void onConsumeCompleted(IoArgs args);
    }

    public interface IoArgsEventListener {
        void onStarted(IoArgs args);

        void onCompleted(IoArgs args);
    }

    public String bufferString() {
        // 丢弃换行符
        return new String(byteBuffer, 0, buffer.position() - 1);
    }
}
