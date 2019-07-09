package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.ReceivePacket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class StringReceivePacket extends ReceivePacket<ByteArrayOutputStream> {
    private String string;

    public StringReceivePacket(int len) {
        length = len;
    }

    public String string() {
        return string;
    }

    @Override
    protected void closeStream(ByteArrayOutputStream stream) throws IOException {
        super.closeStream(stream);
        string = new String(stream.toByteArray());
    }

    @Override
    protected ByteArrayOutputStream createStream() {
        return new ByteArrayOutputStream((int) length);
    }

}
