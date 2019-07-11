package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.SendPacket;

import java.io.ByteArrayInputStream;


/**
 * pure byte array sender
 */
public class BytesSendPacket extends SendPacket<ByteArrayInputStream> {
    private final byte[] bytes;

    public BytesSendPacket(byte[] bytes) {
        this.bytes = bytes;
        this.length = bytes.length;
    }

    @Override
    public byte type() {
        return TYPE_MEMORY_BYTES;
    }

    @Override
    protected ByteArrayInputStream createStream() {
        return new ByteArrayInputStream(bytes);
    }
}
