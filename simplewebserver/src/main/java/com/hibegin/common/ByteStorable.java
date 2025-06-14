package com.hibegin.common;

import com.hibegin.common.util.IOUtil;

import java.io.*;

public class ByteStorable extends AbstractStorable<byte[]> {


    public ByteStorable(byte[] data, String tempFileName) {
        super(data, tempFileName);
    }

    @Override
    protected long getDataSize(byte[] data) {
        return data.length;
    }

    @Override
    protected File serializeToFile(byte[] data, String filePath) {
        File file = new File(filePath + "/" + tempFileName);
        IOUtil.writeBytesToFile(data, file);
        return file;
    }

    @Override
    protected byte[] deserialize(byte[] bytes) {
        return bytes;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (isInMemory()) {
            return new ByteArrayInputStream(data);
        }
        return new FileInputStream(tempFileName);
    }
}
