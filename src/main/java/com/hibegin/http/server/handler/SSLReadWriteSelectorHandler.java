package com.hibegin.http.server.handler;
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

import com.hibegin.common.util.BytesUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

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
public class SSLReadWriteSelectorHandler extends PlainReadWriteSelectorHandler {

    /*
     * An empty ByteBuffer for use when one isn't available, say
     * as a source buffer during initial handshake wraps or for close
     * operations.
     */
    private static ByteBuffer hsBB = ByteBuffer.allocate(0);
    private SSLEngine sslEngine = null;
    /*
     * All I/O goes through these buffers.
     * <P>
     * It might be nice to use a cache of ByteBuffers so we're
     * not alloc/dealloc'ing ByteBuffer's for each new SSLEngine.
     * <P>
     * We use our superclass' requestBB for our application input buffer.
     * Outbound application data is supplied to us by our callers.
     */
    private ByteBuffer inNetBB;
    private ByteBuffer outNetBB;
    /*
     * The FileChannel we're currently transferTo'ing (reading).
     */
    private ByteBuffer fileChannelBB = null;

    /*
     * During our initial handshake, keep track of the next
     * SSLEngine operation that needs to occur:
     *
     *     NEED_WRAP/NEED_UNWRAP
     *
     * Once the initial handshake has completed, we can short circuit
     * handshake checks with initialHSComplete.
     */
    private HandshakeStatus initialHSStatus;
    private boolean initialHSComplete;

    /*
     * We have received the shutdown request by our caller, and have
     * closed our outbound side.
     */
    private boolean shutdown = false;

    /*
     * Constructor for a secure ChannelIO variant.
     */
    public SSLReadWriteSelectorHandler(SocketChannel sc, SelectionKey selectionKey, boolean blocking,
                                       SSLContext sslc) throws IOException {
        super(sc, selectionKey, blocking);

        sslEngine = sslc.createSSLEngine();
        sslEngine.setUseClientMode(false);
        initialHSStatus = HandshakeStatus.NEED_UNWRAP;
        initialHSComplete = false;

        int netBBSize = sslEngine.getSession().getPacketBufferSize();
        inNetBB = ByteBuffer.allocate(netBBSize);
        outNetBB = ByteBuffer.allocate(netBBSize);
        outNetBB.position(0);
        outNetBB.limit(0);

        int appBBSize = sslEngine.getSession().getApplicationBufferSize();
        requestBB = ByteBuffer.allocate(appBBSize);

        while (!doHandshake(selectionKey)) ;
    }

    /*
     * Writes bb to the SocketChannel.
     * <P>
     * Returns true when the ByteBuffer has no remaining data.
     */
    private boolean tryFlush(ByteBuffer bb) throws IOException {
        sc.write(bb);
        return !bb.hasRemaining();
    }

    /*
     * Perform any handshaking processing.
     * <P>
     * If a SelectionKey is passed, register for selectable
     * operations.
     * <P>
     * In the blocking case, our caller will keep calling us until
     * we finish the handshake.  Our reads/writes will block as expected.
     * <P>
     * In the non-blocking case, we just received the selection notification
     * that this channel is ready for whatever the operation is, so give
     * it a try.
     * <P>
     * return:
     *		true when handshake is done.
     *		false while handshake is in progress
     */
    boolean doHandshake(SelectionKey sk) throws IOException {

        SSLEngineResult result;

        if (initialHSComplete) {
            return true;
        }

	/*
     * Flush out the outgoing buffer, if there's anything left in
	 * it.
	 */
        if (outNetBB.hasRemaining()) {

            if (!tryFlush(outNetBB)) {
                return false;
            }

            // See if we need to switch from write to read mode.

            switch (initialHSStatus) {

	    /*
         * Is this the last buffer?
	     */
                case FINISHED:
                    initialHSComplete = true;
                    // Fall-through to reregister need for a Read.

                case NEED_UNWRAP:
                    if (sk != null) {
                        sk.interestOps(SelectionKey.OP_READ);
                    }
                    break;
            }

            return initialHSComplete;
        }


        switch (initialHSStatus) {

            case NEED_UNWRAP:
                if (sc.read(inNetBB) == -1) {
                    sslEngine.closeInbound();
                    return initialHSComplete;
                }

                needIO:
                while (initialHSStatus == HandshakeStatus.NEED_UNWRAP) {
        /*
         * Don't need to resize requestBB, since no app data should
		 * be generated here.
		 */
                    inNetBB.flip();
                    result = sslEngine.unwrap(inNetBB, requestBB);
                    inNetBB.compact();

                    initialHSStatus = result.getHandshakeStatus();

                    switch (result.getStatus()) {

                        case OK:
                            switch (initialHSStatus) {
                                case NOT_HANDSHAKING:
                                    throw new IOException(
                                            "Not handshaking during initial handshake");

                                case NEED_TASK:
                                    initialHSStatus = doTasks();
                                    break;

                                case FINISHED:
                                    initialHSComplete = true;
                                    break needIO;
                            }

                            break;

                        case BUFFER_UNDERFLOW:
            /*
             * Need to go reread the Channel for more data.
		     */
                            if (sk != null) {
                                sk.interestOps(SelectionKey.OP_READ);
                            }
                            break needIO;

                        default: // BUFFER_OVERFLOW/CLOSED:
                            throw new IOException("Received" + result.getStatus() +
                                    "during initial handshaking");
                    }
                }  // "needIO" block.

	    /*
         * Just transitioned from read to write.
	     */
                if (initialHSStatus != HandshakeStatus.NEED_WRAP) {
                    break;
                }

                // Fall through and fill the write buffers.

            case NEED_WRAP:
        /*
         * The flush above guarantees the out buffer to be empty
	     */
                outNetBB.clear();
                result = sslEngine.wrap(hsBB, outNetBB);
                outNetBB.flip();

                initialHSStatus = result.getHandshakeStatus();

                switch (result.getStatus()) {
                    case OK:

                        if (initialHSStatus == HandshakeStatus.NEED_TASK) {
                            initialHSStatus = doTasks();
                        }

                        if (sk != null) {
                            sk.interestOps(SelectionKey.OP_WRITE);
                        }

                        break;

                    default: // BUFFER_OVERFLOW/BUFFER_UNDERFLOW/CLOSED:
                        throw new IOException("Received" + result.getStatus() +
                                "during initial handshaking");
                }
                break;

            default: // NOT_HANDSHAKING/NEED_TASK/FINISHED
                throw new RuntimeException("Invalid Handshaking State" +
                        initialHSStatus);
        } // switch

        return initialHSComplete;
    }

    /*
     * Do all the outstanding handshake tasks in the current Thread.
     */
    private SSLEngineResult.HandshakeStatus doTasks() {

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

    /*
     * Read the channel for more information, then unwrap the
     * (hopefully application) data we get.
     * <P>
     * If we run out of data, we'll return to our caller (possibly using
     * a Selector) to get notification that more is available.
     * <P>
     * Each call to this method will perform at most one underlying read().
     */
    ByteBuffer read() throws IOException {
        SSLEngineResult result;

        if (!initialHSComplete) {
            throw new IllegalStateException();
        }

        int pos = requestBB.position();

        if (sc.read(inNetBB) == -1) {
            sslEngine.closeInbound();  // probably throws exception
            throw new EOFException();
        }

        do {
            resizeRequestBB(inNetBB.remaining());    // guarantees enough room for unwrap
            inNetBB.flip();
            result = sslEngine.unwrap(inNetBB, requestBB);
            inNetBB.compact();

	    /*
         * Could check here for a renegotation, but we're only
	     * doing a simple read/write, and won't have enough state
	     * transitions to do a complete handshake, so ignore that
	     * possibility.
	     */
            switch (result.getStatus()) {

                case BUFFER_UNDERFLOW:
                case OK:
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                        doTasks();
                    }
                    break;

                default:
                    throw new IOException("sslEngine error during data read: " +
                            result.getStatus());
            }
        } while ((inNetBB.position() != 0) &&
                result.getStatus() != Status.BUFFER_UNDERFLOW);
        int readLength = requestBB.position() - pos;
        ByteBuffer byteBuffer = ByteBuffer.allocate(readLength);
        byteBuffer.put(BytesUtil.subBytes(requestBB.array(), pos, readLength));
        return byteBuffer;
    }

    /*
     * Try to flush out any existing outbound data, then try to wrap
     * anything new contained in the src buffer.
     * <P>
     * Return the number of bytes actually consumed from the buffer,
     * but the data may actually be still sitting in the output buffer,
     * waiting to be flushed.
     */
    private int doWrite(ByteBuffer src) throws IOException {
        int retValue = 0;

        if (outNetBB.hasRemaining() && !tryFlush(outNetBB)) {
            return retValue;
        }

	/*
     * The data buffer is empty, we can reuse the entire buffer.
	 */
        outNetBB.clear();

        SSLEngineResult result = sslEngine.wrap(src, outNetBB);
        retValue = result.bytesConsumed();

        outNetBB.flip();

        switch (result.getStatus()) {

            case OK:
                if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                    doTasks();
                }
                break;

            default:
                throw new IOException("sslEngine error during data write: " +
                        result.getStatus());
        }

	/*
     * Try to flush the data, regardless of whether or not
	 * it's been selected.  Odds of a write buffer being full
	 * is less than a read buffer being empty.
	 */
        tryFlush(src);
        if (outNetBB.hasRemaining()) {
            tryFlush(outNetBB);
        }

        return retValue;
    }

    /*
     * Flush any remaining data.
     * <P>
     * Return true when the fileChannelBB and outNetBB are empty.
     */
    boolean dataFlush() throws IOException {
        boolean fileFlushed = true;

        if ((fileChannelBB != null) && fileChannelBB.hasRemaining()) {
            doWrite(fileChannelBB);
            fileFlushed = !fileChannelBB.hasRemaining();
        } else if (outNetBB.hasRemaining()) {
            tryFlush(outNetBB);
        }

        return (fileFlushed && !outNetBB.hasRemaining());
    }

    /*
     * Begin the shutdown process.
     * <P>
     * Close out the SSLEngine if not already done so, then
     * wrap our outgoing close_notify message and try to send it on.
     * <P>
     * Return true when we're done passing the shutdown messsages.
     */
    boolean shutdown() throws IOException {

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
        if (result.getStatus() != Status.CLOSED) {
            throw new SSLException("Improper close state");
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
    public void handleWrite(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.flip();
        while (byteBuffer.hasRemaining() && sc.isOpen()) {
            int len = doWrite(byteBuffer);
            if (len < 0) {
                throw new EOFException();
            }
        }
    }

    @Override
    public ByteBuffer handleRead() throws IOException {
        return read();
    }

    @Override
    public void close() {
        try {
            while (!dataFlush()) {

            }
            do {
            } while (!shutdown());
            super.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
