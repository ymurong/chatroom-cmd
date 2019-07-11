package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.ReceivePacket;

import java.io.ByteArrayOutputStream;

/**
 * define{@link ByteArrayOutputStream} output receiver packet
 *
 * @param <Entity> corresponding entity，should define{@link ByteArrayOutputStream} will be transformed into which data entity
 */
public abstract class AbsByteArrayReceivePacket<Entity> extends ReceivePacket<ByteArrayOutputStream, Entity> {

    public AbsByteArrayReceivePacket(long len) {
        super(len);
    }

    /**
     * stream creation return {@link ByteArrayOutputStream}流
     *
     * @return {@link ByteArrayOutputStream}
     */
    @Override
    protected final ByteArrayOutputStream createStream() {
        return new ByteArrayOutputStream((int) length);
    }
}