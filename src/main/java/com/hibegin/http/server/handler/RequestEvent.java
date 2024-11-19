package com.hibegin.http.server.handler;

import java.nio.channels.SelectionKey;

public class RequestEvent {

    private final SelectionKey selectionKey;
    private final byte[] requestBody;

    public RequestEvent(SelectionKey selectionKey, byte[] data) {
        this.selectionKey = selectionKey;
        this.requestBody = data;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    public byte[] getRequestBody() {
        return requestBody;
    }
}
