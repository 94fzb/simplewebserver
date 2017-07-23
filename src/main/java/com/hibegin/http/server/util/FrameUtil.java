package com.hibegin.http.server.util;

import com.hibegin.common.util.BytesUtil;

public class FrameUtil {

    public static byte[] wrapperData(byte[] bytes) {
        return BytesUtil.mergeBytes(lengthToBytes(bytes.length), new byte[]{0}, new byte[]{0}, streamId(1), bytes);
    }

    private static byte[] lengthToBytes(int number) {
        byte[] targets = new byte[3];
        targets[0] = (byte) ((number >> 16) & 0xff);// 次高位
        targets[1] = (byte) ((number >> 8) & 0xff);// 次低位
        targets[2] = (byte) (number & 0xff);// 最低位
        return targets;
    }

    private static byte[] streamId(int streamId) {
        byte[] targets = new byte[4];
        targets[0] = (byte) (streamId >>> 24);// 最高位,无符号右移。
        targets[1] = (byte) ((streamId >> 16) & 0xff);// 次高位
        targets[2] = (byte) ((streamId >> 8) & 0xff);// 次低位
        targets[3] = (byte) (streamId & 0xff);// 最低位
        return targets;
    }

}
