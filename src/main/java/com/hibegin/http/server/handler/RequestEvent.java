package com.hibegin.http.server.handler;

import java.io.File;
import java.nio.channels.SelectionKey;

class RequestEvent {

    private SelectionKey selectionKey;
    private File file;

    public RequestEvent(SelectionKey key, File generatorRequestTempFile) {
        this.selectionKey = key;
        this.file = generatorRequestTempFile;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
