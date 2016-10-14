package com.hibegin.common.util;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IOUtil {

    private static final Logger LOGGER = LoggerUtil.getLogger(IOUtil.class);

    public static byte[] getByteByInputStream(InputStream in) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] tempByte = new byte[1024];
        try {
            int length;
            while ((length = in.read(tempByte)) != -1) {
                bout.write(tempByte, 0, length);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
        return bout.toByteArray();
    }

    public static String getStringInputStream(InputStream in) {
        return new String(getByteByInputStream(in));
    }

    public static void writeStrToFile(String str, File file) {
        writeBytesToFile(str.getBytes(), file);
    }

    public static void writeBytesToFile(byte[] bytes, File file) {
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.write(bytes);
            out.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }
}
