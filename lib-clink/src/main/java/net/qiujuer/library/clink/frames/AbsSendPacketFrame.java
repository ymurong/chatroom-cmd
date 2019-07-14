package net.qiujuer.library.clink.frames;

import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;

import java.io.IOException;


public abstract class AbsSendPacketFrame extends AbsSendFrame {
    protected volatile SendPacket<?> packet;

    public AbsSendPacketFrame(int length, byte type, byte flag, short identifier, SendPacket packet) {
        super(length, type, flag, identifier);
        this.packet = packet;
    }

    public synchronized SendPacket getPacket() {
        return packet;
    }

    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        if (packet == null && !isSending()) {
            // canceled, and has not sent any data, return end directly, send next frame
            return true;
        }
        return super.handle(args);
    }

    @Override
    public final synchronized Frame nextFrame() {
        return packet == null ? null : buildNextFrame();
    }


    //True, current frame has not sent any data
    public final synchronized boolean abort() {
        boolean isSending = isSending();
        if (isSending) {
            fillDirtyDataOnAbort();
        }
        packet = null;
        return !isSending;
    }

    protected void fillDirtyDataOnAbort() {

    }

    protected abstract Frame buildNextFrame();

}
