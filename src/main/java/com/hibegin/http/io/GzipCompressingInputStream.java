package com.hibegin.http.io;

import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public class GzipCompressingInputStream extends InputStream {
    private static final int GZIP_MAGIC = 0x8b1f;
    private final CRC32 crc = new CRC32();
    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    private final InputStream sourceStream;
    private final byte[] buffer = new byte[1024];
    private boolean headerWritten = false;
    private boolean footerWritten = false;
    private boolean closed = false;

    public GzipCompressingInputStream(InputStream sourceStream) {
        this.sourceStream = sourceStream;
    }

    @Override
    public int read() throws IOException {
        if (!headerWritten) {
            writeHeader();
        }

        if (!deflater.finished()) {
            if (deflater.needsInput()) {
                int len = sourceStream.read(buffer);
                if (len == -1) {
                    deflater.finish();
                } else {
                    crc.update(buffer, 0, len);
                    deflater.setInput(buffer, 0, len);
                }
            }

            byte[] outputBuffer = new byte[1];
            if (deflater.deflate(outputBuffer, 0, 1) > 0) {
                return outputBuffer[0] & 0xff;
            }
        }

        if (!footerWritten) {
            writeFooter();
            footerWritten = true;
        }

        return -1;
    }

    private void writeHeader() throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(header);
        dos.writeShort(GZIP_MAGIC);
        dos.writeByte(Deflater.DEFLATED);
        dos.writeByte(0); // Flags
        dos.writeInt(0); // Modification time
        dos.writeByte(0); // Extra flags
        dos.writeByte(0); // Operating system
        headerWritten = true;
    }

    private void writeFooter() throws IOException {
        ByteArrayOutputStream footer = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(footer);
        dos.writeInt((int) crc.getValue());
        dos.writeInt(deflater.getTotalIn());
        footerWritten = true;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }

        if (!headerWritten) {
            writeHeader();
        }

        int totalBytesDeflated = 0;
        while (totalBytesDeflated < len) {
            if (deflater.needsInput()) {
                int bytesRead = sourceStream.read(buffer);
                if (bytesRead == -1) {
                    deflater.finish();
                    if (deflater.finished() && !footerWritten) {
                        writeFooter();
                        footerWritten = true;
                    }
                } else {
                    crc.update(buffer, 0, bytesRead);
                    deflater.setInput(buffer, 0, bytesRead);
                }
            }

            int bytesDeflated = deflater.deflate(b, off + totalBytesDeflated, len - totalBytesDeflated, Deflater.SYNC_FLUSH);
            if (bytesDeflated == 0 && deflater.finished()) {
                break;
            }
            totalBytesDeflated += bytesDeflated;
        }

        if (totalBytesDeflated == 0 && footerWritten) {
            return -1;
        }

        return totalBytesDeflated;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            sourceStream.close();
            deflater.end();
            closed = true;
        }
    }
}