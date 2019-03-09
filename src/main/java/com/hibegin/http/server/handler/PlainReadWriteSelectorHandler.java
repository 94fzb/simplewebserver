package com.hibegin.http.server.handler;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.LoggerUtil;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlainReadWriteSelectorHandler implements ReadWriteSelectorHandler {

    private static final Logger LOGGER = LoggerUtil.getLogger(PlainReadWriteSelectorHandler.class);

    protected ByteBuffer requestBB;
    protected SocketChannel sc;
    private final ReentrantLock lock = new ReentrantLock();
    private static final int MAX_REQUEST_BB_SIZE = 64 * 1024;

    public PlainReadWriteSelectorHandler(SocketChannel sc) {
        this.sc = sc;
        this.requestBB = ByteBuffer.allocate(1024);
    }

    @Override
    public void handleWrite(ByteBuffer byteBuffer) throws IOException {
        lock.lock();
        try {
            while (byteBuffer.hasRemaining() && sc.isOpen()) {
                int len = sc.write(byteBuffer);
                if (len < 0) {
                    throw new EOFException();
                }
            }
        } finally {
            lock.unlock();
        }

    }

    @Override
    public ByteBuffer handleRead() throws IOException {
        int length = sc.read(requestBB);
        if (length != -1) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(length);
            byteBuffer.put(BytesUtil.subBytes(requestBB.array(), 0, length));
            resizeRequestBB(length);
            return byteBuffer;
        }
        close();
        throw new EOFException();
    }

    void resizeRequestBB(int remaining) {
        if (requestBB.remaining() < remaining) {
            int bbSize = requestBB.capacity() * 2;
            //Expand buffer for large request
            requestBB = ByteBuffer.allocate(bbSize > MAX_REQUEST_BB_SIZE ? MAX_REQUEST_BB_SIZE : bbSize);
        } else {
            requestBB = ByteBuffer.allocate(requestBB.capacity());
        }
    }

    @Override
    public void close() {
        try {
            sc.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "close SocketChannel", e);
        }
    }

    @Override
    public SocketChannel getChannel() {
        return sc;
    }
}
