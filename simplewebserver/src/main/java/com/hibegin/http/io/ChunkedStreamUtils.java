package com.hibegin.http.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ChunkedStreamUtils {

    public static byte[] convertChunkedStream(InputStream chunkedStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = chunkedStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }

        String chunkedData = byteArrayOutputStream.toString();
        StringBuilder result = new StringBuilder();

        int index = 0;
        while (index < chunkedData.length()) {
            // 找到下一个块的长度
            int chunkSizeEndIndex = chunkedData.indexOf("\r\n", index);
            String chunkSizeHex = chunkedData.substring(index, chunkSizeEndIndex);
            int chunkSize = Integer.parseInt(chunkSizeHex, 16);

            if (chunkSize == 0) {
                break; // 读取完毕
            }

            // 读取块数据
            index = chunkSizeEndIndex + 2; // 跳过 "\r\n"
            String chunkData = chunkedData.substring(index, index + chunkSize);
            result.append(chunkData);

            // 跳过块末尾的 "\r\n"
            index += chunkSize + 2;
        }

        return result.toString().getBytes();
    }
}
