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
    private static final int MAX_REQUEST_BB_SIZE = 8 * 1024;
    private static final int INIT_REQUEST_BB_SIZE = 1024;
    final ReentrantLock writeLock = new ReentrantLock();
    final ReentrantLock readLock = new ReentrantLock();
    protected ByteBuffer requestBB;
    protected SocketChannel sc;

    public PlainReadWriteSelectorHandler(SocketChannel sc) {
        this.sc = sc;
        this.requestBB = ByteBuffer.allocate(INIT_REQUEST_BB_SIZE);
    }

    @Override
    public void handleWrite(ByteBuffer byteBuffer) throws IOException {
        writeLock.lock();
        try {
            while (byteBuffer.hasRemaining() && sc.isOpen()) {
                int len = sc.write(byteBuffer);
                if (len < 0) {
                    throw new EOFException();
                }
            }
        } finally {
            writeLock.unlock();
        }

    }

    @Override
    public ByteBuffer handleRead() throws IOException {
        readLock.lock();
        try {
            checkRequestBB();
            int length = sc.read(requestBB);
            if (length != -1) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(length);
                byteBuffer.put(BytesUtil.subBytes(requestBB.array(), 0, length));
                resizeRequestBB(length);
                return byteBuffer;
            }
            throw new EOFException();
        } catch (IOException e) {
            close();
            throw e;
        } finally {
            readLock.unlock();
        }
    }

    void checkRequestBB() {
        if (requestBB.capacity() == 0) {
            requestBB = ByteBuffer.allocate(INIT_REQUEST_BB_SIZE);
        }
    }

    void resizeRequestBB(int remaining) {
        if (requestBB.remaining() < remaining) {
            int bbSize = requestBB.capacity() * 2;
            //Expand buffer for large request
            requestBB = ByteBuffer.allocate(Math.min(bbSize, MAX_REQUEST_BB_SIZE));
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

    @Override
    public void flushRequestBB() {
        readLock.lock();
        requestBB = ByteBuffer.allocate(0);
        readLock.unlock();
    }
}
