package net.qiujuer.library.clink.box;

public class StringSendPacket extends BytesSendPacket {

    /**
     * String is bytes during delivery, so we just relay the bytes and send as bytes
     *
     * @param msg String
     */
    public StringSendPacket(String msg) {
        super(msg.getBytes());
    }

    @Override
    public byte type() {
        return TYPE_MEMORY_STRING;
    }
}