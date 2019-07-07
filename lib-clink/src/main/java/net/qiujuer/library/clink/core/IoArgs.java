package net.qiujuer.library.clink.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class IoArgs {
    private int limit = 256;
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);

    /**
     * readFrom data from bytes into buffer
     *
     * @param bytes
     * @param offset
     * @return
     */
    public int readFrom(byte[] bytes, int offset) {
        int size = Math.min(bytes.length - offset, buffer.remaining());
        // This method transfers bytes into this buffer from the given source array.
        buffer.put(bytes, offset, size);
        return size;
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
        this.limit = limit;
    }

    public void writeLength(int total) {
        buffer.putInt(total);
    }


    public int readLength() {
        return buffer.getInt();
    }

    public int capacity() {
        return buffer.capacity();
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
