package com.hibegin.http.server.handler;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.LoggerUtil;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlainReadWriteSelectorHandler implements ReadWriteSelectorHandler {

    private static final Logger LOGGER = LoggerUtil.getLogger(PlainReadWriteSelectorHandler.class);

    private static int INIT_BYTE_BUFFER_SIZE = 4096;
    protected ByteBuffer requestBB;
    protected SocketChannel sc;
    protected SelectionKey selectionKey;
    private int currentReadSize;
    private ByteBuffer all = ByteBuffer.allocate(0);

    public PlainReadWriteSelectorHandler(SocketChannel sc, SelectionKey selectionKey) {
        this.sc = sc;
        this.selectionKey = selectionKey;
        this.requestBB = ByteBuffer.allocate(INIT_BYTE_BUFFER_SIZE);
    }

    @Override
    public void handleWrite(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.flip();
        while (byteBuffer.hasRemaining() && sc.isOpen()) {
            int len = sc.write(byteBuffer);
            if (len < 0) {
                throw new EOFException();
            }
        }
    }

    @Override
    public ByteBuffer handleRead() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(requestBB.capacity());
        int length = sc.read(byteBuffer);
        resizeRequestBB(length);
        if (length != -1) {
            int t = all.array().length;
            ByteBuffer buffer = ByteBuffer.allocate(length + t);
            buffer.put(all.array());
            buffer.put(BytesUtil.subBytes(byteBuffer.array(), 0, length));
            all = buffer;
            currentReadSize += length;
            return byteBuffer;
        }
        close();
        throw new EOFException();
    }

    protected void resizeRequestBB(int remaining) {
        if (requestBB.remaining() < remaining) {
            // Expand buffer for large request
            ByteBuffer bb = ByteBuffer.allocate(requestBB.capacity() * 2);
            requestBB.flip();
            bb.put(requestBB);
            requestBB = bb;
        }
    }

    public void close() {
        try {
            sc.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "close SocketChannel", e);
        }
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return all;
    }

    @Override
    public int currentReadSize() {
        return currentReadSize;
    }

    public SocketChannel getChannel() {
        return sc;
    }
}
