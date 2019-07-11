package net.qiujuer.library.clink.core;

import java.io.IOException;
import java.io.OutputStream;

public abstract class ReceivePacket<Stream extends OutputStream, Entity> extends Packet<Stream> {
    // define current receivepacket final entity
    private Entity entity;

    public ReceivePacket(long len) {
        this.length = len;
    }

    /**
     * get the entity that finally received
     *
     * @return data entity
     */
    public Entity entity() {
        return entity;
    }

    /**
     * transform to corresponding entity according to received stream
     *
     * @param stream {@link OutputStream}
     * @return entity
     */
    protected abstract Entity buildEntity(Stream stream);

    /**
     * close stream then transform the stream content to corresponding entity
     *
     * @param stream the stream to close
     * @throws IOException
     */
    @Override
    protected final void closeStream(Stream stream) throws IOException {
        super.closeStream(stream);
        entity = buildEntity(stream);
    }
}
