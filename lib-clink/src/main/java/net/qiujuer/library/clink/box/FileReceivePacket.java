package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.ReceivePacket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class FileReceivePacket extends ReceivePacket<FileOutputStream, File> {
    private File file;

    public FileReceivePacket(long len, File file) {
        super(len);
        this.file = file;
    }

    @Override
    public byte type() {
        return TYPE_STREAM_FILE;
    }

    /**
     * return the input file directly when we transform stream to entity
     *
     * @param stream 文件传输流
     * @return File
     */
    @Override
    protected File buildEntity(FileOutputStream stream) {
        return file;
    }

    @Override
    protected FileOutputStream createStream() {
        try {
            return new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }
}
