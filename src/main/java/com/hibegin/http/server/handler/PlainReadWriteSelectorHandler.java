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

    private static Logger LOGGER = LoggerUtil.getLogger(PlainReadWriteSelectorHandler.class);

    static private int requestBBSize = 4096;
    protected ByteBuffer requestBB;
    protected SocketChannel sc;
    protected SelectionKey selectionKey;
    private int currentReadSize;
    private ByteBuffer all = ByteBuffer.allocate(0);

    public PlainReadWriteSelectorHandler(SocketChannel sc, SelectionKey selectionKey, boolean blocking)
            throws IOException {
        this.sc = sc;
        this.selectionKey = selectionKey;
        sc.configureBlocking(blocking);
        requestBB = ByteBuffer.allocate(requestBBSize);
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


    /*
     * All of the inbound request data lives here until we determine
     * that we've read everything, then we pass that data back to the
     * caller.
     */

    @Override
    public ByteBuffer handleRead() throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(requestBBSize);
        int length = sc.read(byteBuffer);
        resizeRequestBB(length);
        if (length != -1) {
            int t = 0;
            if (all != null) {
                t = all.array().length;
            }
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

    /*
     * Return a ByteBuffer with "remaining" space to work.  If you have to
     * reallocate the ByteBuffer, copy the existing info into the new buffer.
     */
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
