package com.hibegin.common.util;

import java.nio.charset.StandardCharsets;

public class UrlEncodeUtils {

    private static String percentEncode(char c) {
        byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%%%02X", b));
        }
        return sb.toString();
    }

    public static String encodeUrl(String url) {
        StringBuilder encoded = new StringBuilder();
        for (char c : url.toCharArray()) {
            if (isUnsafeCharacter(c)) {
                encoded.append(percentEncode(c));
            } else {
                encoded.append(c);
            }
        }
        return encoded.toString();
    }

    private static boolean isUnsafeCharacter(char c) {
        // 检查字符是否为需要编码的字符，保留保留字符和子分隔符
        return !(isUnreservedCharacter(c) || isReservedCharacter(c));
    }

    private static boolean isUnreservedCharacter(char c) {
        // 不需要编码的普通字符：字母、数字、- . _ ~
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '.' || c == '_' || c == '~';
    }

    private static boolean isReservedCharacter(char c) {
        // 保留字符和子分隔符
        return ":/?#[]@!$&'()*+,;=".indexOf(c) != -1;
    }
}
