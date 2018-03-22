package com.hibegin.http.server.util;

import com.hibegin.common.util.IOUtil;

import java.io.File;
import java.io.IOException;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FileCacheKit {

    private static File NOT_FOUND_FILE = new File(PathUtil.getTempPath() + "/" + UUID.randomUUID().toString());
    private static Queue<File> needDeleteFileQueue = new ConcurrentLinkedQueue<>();
    private static ScheduledExecutorService scheduledThreadPoolExecutor = Executors.newSingleThreadScheduledExecutor();

    static {
        scheduledThreadPoolExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                while (!needDeleteFileQueue.isEmpty()) {
                    needDeleteFileQueue.poll().delete();
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public static File generatorRequestTempFile(int flag, byte[] bytes) {
        if (bytes != null && bytes.length > 0) {
            try {
                File file = File.createTempFile("cache-", suffix(flag), new File(PathUtil.getTempPath()));
                IOUtil.writeBytesToFile(bytes, file);
                return file;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return NOT_FOUND_FILE;
    }

    private static String suffix(int flag) {
        return ".tmp." + flag;
    }

    public static void cleanByFlag(int flag) {
        File[] files = new File(PathUtil.getTempPath()).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(suffix(flag))) {
                    file.delete();
                }
            }
        }
    }

    public static void deleteCache(File file) {
        needDeleteFileQueue.add(file);
    }
}
