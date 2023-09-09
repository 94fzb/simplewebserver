package com.hibegin.http.server.handler;

import com.hibegin.common.util.IOUtil;

import java.io.File;
import java.nio.channels.SelectionKey;
import java.util.Objects;

class RequestEvent {

    private final SelectionKey selectionKey;
    private File file;
    private byte[] requestBytes;
    private final long length;

    public long getLength() {
        return length;
    }

    public RequestEvent(SelectionKey key, byte[] requestBytes) {
        this.length = requestBytes.length;
        this.selectionKey = key;
        this.requestBytes = requestBytes;
    }

    public RequestEvent(SelectionKey key, File file) {
        this.length = file.length();
        this.selectionKey = key;
        this.file = file;

    }

    public void deleteFile() {
        if (Objects.nonNull(file)) {
            file.delete();
        }
    }

    public byte[] getRequestBytes() {
        if (Objects.nonNull(file)) {
            return IOUtil.getByteByFile(file);
        }
        return requestBytes;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }
}
