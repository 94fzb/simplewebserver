package com.hibegin.common.util;

import com.hibegin.http.server.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LoggerUtil {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static final String LOG_FILE_SUFFIX = ".log";
    private static FileHandler fileHandler;
    private static final Lock loadLock = new ReentrantLock();

    static {
        try {
            File fileName = new File(getLogFilePath());
            if (!fileName.exists()) {
                fileName.getParentFile().mkdirs();
            }
            fileHandler = new FileHandler(fileName.toString(), true);
            fileHandler.setFormatter(new SimpleFormatter());
        } catch (IOException e) {
            getLogger(LoggerUtil.class).severe("Init logger error " + e.getMessage());
        }
    }

    private LoggerUtil() {
    }

    public static Logger getLogger(Class<?> clazz) {
        loadLock.lock();
        try {
            Logger logger = Logger.getLogger(clazz.getName());
            //避免重复添加 handle
            if (Arrays.stream(logger.getHandlers()).anyMatch(x -> Objects.equals(x, fileHandler))) {
                return logger;
            }
            try {
                if (fileHandler != null) {
                    logger.addHandler(fileHandler);
                }
                logger.setLevel(Level.ALL);
            } catch (Exception e) {
                logger.severe("Init logger error " + e.getMessage());
            }
            return logger;
        } finally {
            loadLock.unlock();
        }
    }

    private synchronized static String getLogFilePath() {
        StringBuilder logFilePath = new StringBuilder();
        logFilePath.append(PathUtil.getLogPath());
        File file = new File(logFilePath.toString());
        if (!file.exists()) {
            file.mkdir();
        }

        logFilePath.append(File.separatorChar);
        logFilePath.append(sdf.format(new Date()));
        logFilePath.append(LOG_FILE_SUFFIX);

        return logFilePath.toString();
    }

    /**
     * 记录完善的异常日志信息(包括堆栈信息)
     */
    public static String recordStackTraceMsg(Exception e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        e.printStackTrace(writer);
        StringBuffer buffer = stringWriter.getBuffer();
        return buffer.toString();
    }


}
