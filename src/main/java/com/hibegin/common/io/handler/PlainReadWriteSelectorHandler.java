package com.hibegin.common.io.handler;

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
    private final int maxRequestBbSize;
    private static final int INIT_REQUEST_BB_SIZE = 8 * 1024;
    final ReentrantLock writeLock = new ReentrantLock();
    final ReentrantLock readLock = new ReentrantLock();
    protected ByteBuffer requestBB;
    protected SocketChannel sc;

    public PlainReadWriteSelectorHandler(SocketChannel sc, int maxRequestBbSize) {
        this.sc = sc;
        this.maxRequestBbSize = maxRequestBbSize;
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
        if (requestBB.remaining() >= remaining) return;

        requestBB.flip();  // limit = 当前写入位置, position = 0

        int newCapacity = Math.min(Math.max(requestBB.limit() + remaining, requestBB.capacity() * 2), maxRequestBbSize);
        ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);

        newBuffer.put(requestBB); // 把全部写入数据 copy 过去（0 到 limit）
        requestBB = newBuffer;
    }

    @Override
    public void close() {
        if (!sc.isOpen()) {
            return;
        }
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
