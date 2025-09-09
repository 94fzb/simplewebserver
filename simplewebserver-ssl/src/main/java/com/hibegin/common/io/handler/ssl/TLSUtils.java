package com.hibegin.common.io.handler.ssl;

import java.nio.ByteBuffer;

public class TLSUtils {

    public static boolean looksLikeTLS(byte[] data) {
        if (data.length < 5) {
            return true; // 数据太少，不足以判断，先假定是 TLS
        }

        int contentType = data[0] & 0xFF;
        int major = data[1] & 0xFF;
        int minor = data[2] & 0xFF;
        int len = ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);

        // ContentType 必须是 TLS 记录层的定义值 (20,21,22,23)
        if (contentType != 20 && contentType != 21 &&
                contentType != 22 && contentType != 23) {
            return false;
        }

        // TLS 1.0~1.3 major 必须是 3
        if (major != 3) {
            return false;
        }

        // minor 应该是 1~4 (TLS1.0=1, TLS1.1=2, TLS1.2=3, TLS1.3=4)

        if (minor < 1 || minor > 4) {
            return false;
        }

        // 长度必须合理 (1 ~ 16384 + 2048 余量)
        if (len < 1 || len > 0x4000 + 2048) {
            return false;
        }

        return true;
    }
}
