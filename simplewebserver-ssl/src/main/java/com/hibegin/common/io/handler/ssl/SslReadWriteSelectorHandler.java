package com.hibegin.common.io.handler.ssl;
/*
 * @(#)ChannelIOSecure.java	1.2 04/07/26
 *
 * Copyright (c) 2004 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * -Redistribution of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MIDROSYSTEMS, INC. ("SUN")
 * AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 * AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
 * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or intended
 * for use in the design, construction, operation or maintenance of any
 * nuclear facility.
 */

import com.hibegin.common.io.handler.PlainReadWriteSelectorHandler;
import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.execption.PlainRequestToSslPortException;

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A helper class which performs I/O using the SSLEngine API.
 * <p>
 * Each connection has a SocketChannel and a SSLEngine that is
 * used through the lifetime of the Channel.  We allocate byte buffers
 * for use as the outbound and inbound network buffers.
 * <p>
 * <PRE>
 * Application Data
 * src      requestBB
 * |           ^
 * |     |     |
 * v     |     |
 * +----+-----|-----+----+
 * |          |          |
 * |       SSL|Engine    |
 * wrap()  |          |          |  unwrap()
 * | OUTBOUND | INBOUND  |
 * |          |          |
 * +----+-----|-----+----+
 * |     |     ^
 * |     |     |
 * v           |
 * outNetBB     inNetBB
 * Net data
 * </PRE>
 * <p>
 * These buffers handle all of the intermediary data for the SSL
 * connection.  To make things easy, we'll require outNetBB be
 * completely flushed before trying to wrap any more data, but we
 * could certainly remove that restriction by using larger buffers.
 * <p>
 * There are many, many ways to handle compute and I/O strategies.
 * What follows is a relatively simple one.  The reader is encouraged
 * to develop the strategy that best fits the application.
 * <p>
 * In most of the non-blocking operations in this class, we let the
 * Selector tell us when we're ready to attempt an I/O operation (by the
 * application repeatedly calling our methods).  Another option would be
 * to attempt the operation and return from the method when no forward
 * progress can be made.
 * <p>
 * There's lots of room for enhancements and improvement in this example.
 * <p>
 * We're checking for SSL/TLS end-of-stream truncation attacks via
 * sslEngine.closeInbound().  When you reach the end of a input stream
 * via a read() returning -1 or an IOException, we call
 * sslEngine.closeInbound() to signal to the sslEngine that no more
 * input will be available.  If the peer's close_notify message has not
 * yet been received, this could indicate a trucation attack, in which
 * an attacker is trying to prematurely close the connection.   The
 * closeInbound() will throw an exception if this condition were
 * present.
 *
 * @author Brad R. Wetmore
 * @author Mark Reinhold
 * @version 1.2, 04/07/26
 */
public class SslReadWriteSelectorHandler extends PlainReadWriteSelectorHandler {

    private static final Logger LOGGER = LoggerUtil.getLogger(SslReadWriteSelectorHandler.class);
    /**
     * An empty ByteBuffer for use when one isn't available, say
     * as a source buffer during initial handshake wraps or for close
     * operations.
     */
    private static final ByteBuffer hsBB = ByteBuffer.allocate(0);
    private static final int MAX_REQUEST_BB_SIZE = 32 * 1024 * 1024;
    private final SSLEngine sslEngine;
    /**
     * All I/O goes through these buffers.
     * <p>
     * It might be nice to use a cache of ByteBuffers so we're
     * not alloc/dealloc'ing ByteBuffer's for each new SSLEngine.
     * <p>
     * We use our superclass' requestBB for our application input buffer.
     * Outbound application data is supplied to us by our callers.
     */
    private final ByteBuffer inNetBB;
    private final ByteBuffer outNetBB;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final boolean disablePlainRead;
    /**
     * During our initial handshake, keep track of the next
     * SSLEngine operation that needs to occur:
     * <p>
     * NEED_WRAP/NEED_UNWRAP
     * <p>
     * Once the initial handshake has completed, we can short circuit
     * handshake checks with initialHSComplete.
     */
    private HandshakeStatus initialHSStatus;
    private boolean initialHSComplete;
    /**
     * We have received the shutdown request by our caller, and have
     * closed our outbound side.
     */
    private boolean shutdown = false;
    private ByteArrayOutputStream writePendingStream = new ByteArrayOutputStream();
    private boolean plain;


    /**
     * Constructor for a secure ChannelIO variant.
     */
    public SslReadWriteSelectorHandler(Selector selector,
                                       SocketChannel sc,
                                       SSLContext sslContext,
                                       int maxRequestBufferSize,
                                       boolean clientMode,
                                       boolean disablePlainRead) throws IOException {
        this(selector, sc, sslContext, maxRequestBufferSize, clientMode, disablePlainRead, null, 0, false);
    }

    public SslReadWriteSelectorHandler(Selector selector,
                                       SocketChannel sc,
                                       SSLContext sslContext,
                                       int maxRequestBufferSize,
                                       boolean clientMode,
                                       boolean disablePlainRead,
                                       String host, int port,
                                       boolean sendSNI) throws IOException {
        super(selector, sc, Math.max(MAX_REQUEST_BB_SIZE, maxRequestBufferSize));
        sslEngine = clientMode ? sslContext.createSSLEngine(host, port) : sslContext.createSSLEngine();
        sslEngine.setUseClientMode(clientMode);
        if (clientMode && sendSNI) {
            SSLParameters sslParams = sslEngine.getSSLParameters();
            SNIHostName serverName = new SNIHostName(host);
            List<SNIServerName> serverNames = Collections.singletonList(serverName);
            sslParams.setServerNames(serverNames);
            sslEngine.setSSLParameters(sslParams);
        }

        initialHSStatus = HandshakeStatus.NEED_UNWRAP;
        initialHSComplete = false;
        this.disablePlainRead = disablePlainRead;

        int netBBSize = sslEngine.getSession().getPacketBufferSize();
        inNetBB = ByteBuffer.allocate(netBBSize);
        outNetBB = ByteBuffer.allocate(netBBSize);
        outNetBB.position(0);
        outNetBB.limit(0);

        int appBBSize = sslEngine.getSession().getApplicationBufferSize();
        requestBB = ByteBuffer.allocate(appBBSize);
        try {
            doHandshake();
        } catch (SSLException e) {
            if (disablePlainRead) {
                throw e;
            }
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    /**
     * Writes bb to the SocketChannel.
     * <p>
     * Returns true when the ByteBuffer has no remaining data.
     */
    private boolean tryFlush(ByteBuffer bb) throws IOException {
        super.handleWrite(bb);
        return !bb.hasRemaining();
    }

    private void resizeRequestBB(int minRemaining) {
        if (requestBB.remaining() >= minRemaining) {
            return;
        }

        requestBB.flip();
        int required = requestBB.remaining() + minRemaining;
        int newCapacity = Math.max(required, requestBB.capacity() * 2);

        ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
        newBuffer.put(requestBB);
        requestBB = newBuffer;
    }

    /**
     * Perform any handshaking processing.
     * <p>
     * If a SelectionKey is passed, register for selectable
     * operations.
     * <p>
     * In the blocking case, our caller will keep calling us until
     * we finish the handshake.  Our reads/writes will block as expected.
     * <p>
     * In the non-blocking case, we just received the selection notification
     * that this channel is ready for whatever the operation is, so give
     * it a try.
     * <p>
     * return:
     * true when handshake is done.
     * false while handshake is in progress
     */
    void doHandshake() throws IOException {
        while (!initialHSComplete) {
            SSLEngineResult result;
            switch (initialHSStatus) {
                case NEED_UNWRAP:
                    if (sc.read(inNetBB) == -1) {
                        sslEngine.closeInbound();
                        throw new EOFException("Connection closed during handshake");
                    }

                    inNetBB.flip();
                    result = sslEngine.unwrap(inNetBB, requestBB);
                    inNetBB.compact();

                    initialHSStatus = result.getHandshakeStatus();

                    switch (result.getStatus()) {
                        case OK:
                            // fallthrough
                            break;
                        case BUFFER_UNDERFLOW:
                            return;
                        case CLOSED:
                            throw new IOException("SSLEngine closed during handshake");
                        default:
                            throw new IOException("Unexpected unwrap result: " + result.getStatus());
                    }

                    break;

                case NEED_WRAP:
                    outNetBB.clear();
                    result = sslEngine.wrap(hsBB, outNetBB);
                    outNetBB.flip();

                    initialHSStatus = result.getHandshakeStatus();

                    switch (result.getStatus()) {
                        case OK:
                            super.handleWrite(outNetBB);
                            break;
                        case CLOSED:
                            throw new IOException("SSLEngine closed during wrap");
                        default:
                            throw new IOException("Unexpected wrap result: " + result.getStatus());
                    }

                    break;

                case NEED_TASK:
                    initialHSStatus = doTasks();
                    break;

                case FINISHED:
                case NOT_HANDSHAKING:
                    initialHSComplete = true;
                    // flush pending write after handshake
                    byte[] byteArray = writePendingStream.toByteArray();
                    if (byteArray.length > 0) {
                        handleWrite(ByteBuffer.wrap(byteArray));
                        writePendingStream = new ByteArrayOutputStream();
                    }
                    return;
                // flush pending write after handshake
            }
        }
    }

    /**
     * Do all the outstanding handshake tasks in the current Thread.
     */
    private HandshakeStatus doTasks() {
        Runnable runnable;

        /*
         * We could run this in a separate thread, but
         * do in the current for now.
         */
        while ((runnable = sslEngine.getDelegatedTask()) != null) {
            runnable.run();
        }
        return sslEngine.getHandshakeStatus();
    }

    private ByteBuffer doSslReadHandle() throws IOException {
        // 用于统计是否陷入死循环
        int overflowAttempts = 0;
        while (inNetBB.hasRemaining()) {
            if (!sc.isOpen()) {
                throw new EOFException();
            }
            SSLEngineResult result = sslEngine.unwrap(inNetBB, requestBB);

            switch (result.getStatus()) {
                case OK:
                    break;

                case BUFFER_UNDERFLOW:
                    inNetBB.compact();  // 留数据给下次 read
                    return ByteBuffer.allocate(0);

                case BUFFER_OVERFLOW:
                    int currentCap = requestBB.capacity();
                    // 防止 memory leak.
                    if (currentCap >= requestBbAllocator.getMaxSize()) {
                        //已经有数据了，可以不扩容，返回调用，去消费
                        if (requestBB.position() > 0) {
                            break;
                        }
                        throw new IOException("unwrap BUFFER_OVERFLOW but no progress; giving up to avoid memory leak. currentCap to limited " + currentCap);
                    }
                    if (overflowAttempts > 32) {
                        //已经有数据了，可以不扩容，返回调用，去消费
                        if (requestBB.position() > 0) {
                            break;
                        }
                        throw new IOException("unwrap BUFFER_OVERFLOW but no progress; giving up to avoid memory leak. overflowAttempts: " + overflowAttempts);
                    }
                    overflowAttempts++;
                    // requestBB 太小，扩容！
                    resizeRequestBB(sslEngine.getSession().getApplicationBufferSize());
                    break;

                case CLOSED:
                    try {
                        sslEngine.closeInbound();
                    } catch (SSLException e) {
                        LOGGER.warning("SSL closed without close_notify: " + e.getMessage());
                    }
                    return ByteBuffer.allocate(0);

                default:
                    throw new IOException("sslEngine error during unwrap: " + result.getStatus());
            }

            if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                doTasks();
            }
        }
        inNetBB.compact();
        requestBB.flip();
        ByteBuffer output = ByteBuffer.allocate(requestBB.remaining());
        output.put(requestBB);
        output.flip();
        flushRequestBB(output.array().length);
        return output;
    }

    /**
     * Read the channel for more information, then unwrap the
     * (hopefully application) data we get.
     * <p>
     * If we run out of data, we'll return to our caller (possibly using
     * a Selector) to get notification that more is available.
     * <p>
     * Each call to this method will perform at most one underlying read().
     */
    @Override
    public ByteBuffer handleRead() throws IOException {
        if (plain) {
            return super.handleRead();
        }
        readLock.lock();
        int readLength;
        try {
            initRequestBB();
            doHandshake();
            if (!initialHSComplete) {
                return ByteBuffer.allocate(0);
            }
            readLength = sc.read(inNetBB);
            if (readLength == -1) {
                // probably throws exception
                sslEngine.closeInbound();
                throw new EOFException();
            }
            inNetBB.flip();
            return doSslReadHandle();
        }
        //not close stream, handle connect state by caller
        catch (SSLException e) {
            if (disablePlainRead) {
                throw new PlainRequestToSslPortException();
            }
            this.plain = true;
            return ByteBuffer.wrap(BytesUtil.subBytes(inNetBB.array(), 0, inNetBB.remaining()));
        } catch (IOException e) {
            close();
            throw e;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Try to flush out any existing outbound data, then try to wrap
     * anything new contained in the src buffer.
     * <p>
     * Return the number of bytes actually consumed from the buffer,
     * but the data may actually be still sitting in the output buffer,
     * waiting to be flushed.
     */
    private int doWrite(ByteBuffer src) throws IOException {
        if (outNetBB.hasRemaining() && !tryFlush(outNetBB)) {
            return 0;
        }

        outNetBB.clear();

        // 控制最多 wrap 一段
        int maxWrapLen = sslEngine.getSession().getApplicationBufferSize();
        int oldLimit = src.limit();
        if (src.remaining() > maxWrapLen) {
            src.limit(src.position() + maxWrapLen);
        }

        SSLEngineResult result = sslEngine.wrap(src, outNetBB);
        src.limit(oldLimit); // 恢复原来的 limit

        int consumed = result.bytesConsumed();
        outNetBB.flip();

        if (result.getStatus() != Status.OK) {
            throw new IOException("sslEngine error during data write: " +
                    result.getStatus());
        }

        tryFlush(outNetBB);
        return consumed;
    }

    /**
     * Flush any remaining data.
     * <p>
     * Return true when the fileChannelBB and outNetBB are empty.
     */
    private boolean dataFlush() throws IOException {
        if (outNetBB.hasRemaining()) {
            tryFlush(outNetBB);
        }

        return !outNetBB.hasRemaining();
    }

    /**
     * Begin the shutdown process.
     * <p>
     * Close out the SSLEngine if not already done so, then
     * wrap our outgoing close_notify message and try to send it on.
     * <p>
     * Return true when we're done passing the shutdown messsages.
     */
    private boolean shutdown() throws IOException {

        if (!shutdown) {
            sslEngine.closeOutbound();
            shutdown = true;
        }

        if (outNetBB.hasRemaining() && tryFlush(outNetBB)) {
            return false;
        }

        /*
         * By RFC 2616, we can "fire and forget" our close_notify
         * message, so that's what we'll do here.
         */
        outNetBB.clear();
        SSLEngineResult result = sslEngine.wrap(hsBB, outNetBB);
        if (result.getStatus() != Status.OK && result.getStatus() != Status.CLOSED) {
            throw new SSLException("Unexpected status during shutdown: " + result.getStatus());
        }
        outNetBB.flip();

        /*
         * We won't wait for a select here, but if this doesn't work,
         * we'll cycle back through on the next select.
         */
        if (outNetBB.hasRemaining()) {
            tryFlush(outNetBB);
        }

        return (!outNetBB.hasRemaining() &&
                (result.getHandshakeStatus() != HandshakeStatus.NEED_WRAP));
    }

    @Override
    public boolean isPlain() {
        return plain;
    }

    @Override
    public void handleWrite(ByteBuffer byteBuffer) throws IOException {
        if (plain) {
            super.handleWrite(byteBuffer);
            return;
        }
        writeLock.lock();
        try {
            doHandshake();
            if (!initialHSComplete) {
                writePendingStream.write(byteBuffer.array());
                return;
            }
            while (byteBuffer.hasRemaining() && sc.isOpen()) {
                int len = doWrite(byteBuffer);
                if (len < 0) {
                    throw new EOFException();
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        this.cleanRequestBB();
        if (closed.compareAndSet(false, true)) {
            try {
                if (plain) {
                    return;
                }
                if (!dataFlush()) {
                    return;
                }
                shutdown();
            } catch (IOException e) {
                if (EnvKit.isDevMode()) {
                    LOGGER.log(Level.SEVERE, "", e);
                }
            } finally {
                super.close();
            }
        }
    }
}
