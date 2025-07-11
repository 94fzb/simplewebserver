package com.hibegin.common.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public class UrlDecodeUtils {

    public static String decodePath(String path, String charset) {
        return decodePercentOnly(path, Charset.forName(charset));
    }

    private static String decodePercentOnly(String encoded, Charset charset) {
        if (encoded == null) return null;

        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < encoded.length(); ) {
            char c = encoded.charAt(i);
            if (c == '%') {
                // 确认后面有两个字符且是16进制数字
                if (i + 2 < encoded.length()) {
                    char c1 = encoded.charAt(i + 1);
                    char c2 = encoded.charAt(i + 2);
                    int hi = Character.digit(c1, 16);
                    int lo = Character.digit(c2, 16);
                    if (hi != -1 && lo != -1) {
                        byteBuffer.write((hi << 4) + lo);
                        i += 3;
                        continue;
                    }
                }
                // 不合法的%编码，直接原样添加%
            } else {
                // 先把已经收集的字节解码成字符写入结果
                if (byteBuffer.size() > 0) {
                    result.append(new String(byteBuffer.toByteArray(), charset));
                    byteBuffer.reset();
                }
                // 不是百分号，直接追加
            }
            result.append(c);
            i++;
        }

        // 处理尾部的剩余字节
        if (byteBuffer.size() > 0) {
            result.append(new String(byteBuffer.toByteArray(), charset));
        }

        return result.toString();
    }

    public static void main(String[] args) {
        System.out.println(UrlDecodeUtils.decodePath("/path/with%20spaces+and%2Bplus", "UTF-8"));
    }
}
