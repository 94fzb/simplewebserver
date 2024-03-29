package com.hibegin.common.util;

/**
 * 字节，字节数组(合并,截取)
 */
public class BytesUtil {

    public static byte[] mergeBytes(byte[]... bytes) {
        int bytesSize = 0;
        for (byte[] bs : bytes) {
            bytesSize += bs.length;
        }
        byte[] nBytes = new byte[bytesSize];
        int size = 0;
        for (byte[] bs : bytes) {
            System.arraycopy(bs, 0, nBytes, size, bs.length);
            size += bs.length;
        }
        return nBytes;
    }

    public static byte[] subBytes(byte[] b, int start, int length) {
        int nLength = Math.min(b.length, length);
        byte[] bytes = new byte[nLength];
        System.arraycopy(b, start, bytes, 0, nLength);
        return bytes;
    }
}
