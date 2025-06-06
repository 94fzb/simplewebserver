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
    protected final int maxRequestBbSize;
    private static final int INIT_REQUEST_BB_SIZE = 8 * 1024;
    private static final int MAX_REQUEST_BB_SIZE = 512 * 1024;
    final ReentrantLock writeLock = new ReentrantLock();
    final ReentrantLock readLock = new ReentrantLock();
    protected ByteBuffer requestBB;
    protected SocketChannel sc;
    private volatile int lastReadLength = 0;

    public PlainReadWriteSelectorHandler(SocketChannel sc) {
        this(sc, MAX_REQUEST_BB_SIZE);
    }

    public PlainReadWriteSelectorHandler(SocketChannel sc, int maxRequestBbSize) {
        this.sc = sc;
        this.maxRequestBbSize = Math.max(Math.min(maxRequestBbSize, MAX_REQUEST_BB_SIZE), INIT_REQUEST_BB_SIZE);
        this.requestBB = ByteBuffer.allocate(0);
    }

    @Override
    public boolean isPlain() {
        return true;
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
            initRequestBB();
            int length = sc.read(requestBB);
            if (length != -1) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(length);
                byteBuffer.put(BytesUtil.subBytes(requestBB.array(), 0, length));
                flushRequestBB(length);
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

    void initRequestBB() {
        if (requestBB.capacity() > 0) {
            return;
        }
        int bbSize = lastReadLength == 0 ? INIT_REQUEST_BB_SIZE : lastReadLength * 2;
        //Expand buffer for large request
        requestBB = ByteBuffer.allocate(Math.min(bbSize, maxRequestBbSize));
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

    void flushRequestBB(int lastReadLength) {
        readLock.lock();
        requestBB = ByteBuffer.allocate(0);
        this.lastReadLength = lastReadLength;
        readLock.unlock();
    }
}
