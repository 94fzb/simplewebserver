package com.hibegin.http.server.handler;

import com.hibegin.common.AbstractStorable;
import com.hibegin.common.util.IOUtil;

import java.io.File;
import java.nio.channels.SelectionKey;

class RequestEventStorable extends AbstractStorable<RequestEvent> {

    private final SelectionKey selectionKey;

    public RequestEventStorable(SelectionKey key, byte[] requestBytes, String fileName) {
        super(new RequestEvent(key, requestBytes), fileName);
        this.selectionKey = key;
    }

    @Override
    protected long getDataSize(RequestEvent data) {
        return data.getRequestBody().length;
    }

    @Override
    protected File serializeToFile(RequestEvent data, String filePath) {
        File file = new File(filePath + "/" + tempFileName);
        IOUtil.writeBytesToFile(data.getRequestBody(), file);
        return file;
    }

    @Override
    protected RequestEvent deserialize(byte[] bytes) {
        return new RequestEvent(selectionKey, bytes);
    }
}
