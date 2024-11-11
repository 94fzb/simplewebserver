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
import java.util.logging.*;

public class LoggerUtil {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private static final String LOG_FILE_SUFFIX = ".log";
    private static FileHandler fileHandler;
    private static final Lock loadLock = new ReentrantLock();

    private LoggerUtil() {
    }

    public static FileHandler buildFileHandle() {
        try {
            File fileName = new File(getLogFilePath());
            if (!fileName.exists()) {
                fileName.getParentFile().mkdirs();
            }
            FileHandler handler = new FileHandler(fileName.toString(), true);
            handler.setFormatter(new SimpleFormatter());
            return handler;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void initFileHandle(FileHandler _fileHandler) {
        fileHandler = _fileHandler;
    }

    public static FileHandler getFileHandler() {
        return fileHandler;
    }

    private static CustomConsoleHandler buildOutConsoleHandler() {
        CustomConsoleHandler consoleHandler = new CustomConsoleHandler();
        consoleHandler.setFormatter(new ColorfulFormatter());
        consoleHandler.setOutputStream(System.out);
        consoleHandler.setFilter((p) -> p.getLevel().intValue() >= Level.INFO.intValue());
        consoleHandler.setFormatter(new ConsoleHandler().getFormatter());
        return consoleHandler;
    }

    private static CustomConsoleHandler buildErrConsoleHandler() {
        CustomConsoleHandler consoleHandler = new CustomConsoleHandler();
        consoleHandler.setFormatter(new ColorfulFormatter());
        consoleHandler.setOutputStream(System.err);
        consoleHandler.setFilter((p) -> p.getLevel().intValue() <= Level.INFO.intValue());
        consoleHandler.setFormatter(new ConsoleHandler().getFormatter());
        return consoleHandler;
    }

    public static Logger getLogger(Class<?> clazz) {
        loadLock.lock();
        try {
            Logger logger = Logger.getLogger(clazz.getName());
            //避免重复添加 handle
            if (Arrays.stream(logger.getHandlers()).anyMatch(x -> Objects.equals(x, fileHandler))) {
                return logger;
            }
            logger.addHandler(buildErrConsoleHandler());
            logger.addHandler(buildOutConsoleHandler());
            try {
                if (Objects.isNull(fileHandler)) {
                    fileHandler = buildFileHandle();
                }
                logger.addHandler(fileHandler);
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
    public static String recordStackTraceMsg(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        e.printStackTrace(writer);
        StringBuffer buffer = stringWriter.getBuffer();
        return buffer.toString();
    }

    // 自定义 Formatter 实现控制台日志的颜色输出
    static class ColorfulFormatter extends Formatter {
        private static final String RESET = "\u001B[0m";
        private static final String RED = "\u001B[31m";
        private static final String YELLOW = "\u001B[33m";
        private static final String BLUE = "\u001B[34m";
        private static final String GREEN = "\u001B[32m";

        @Override
        public String format(LogRecord record) {
            String color;
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                color = RED;
            } else if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                color = YELLOW;
            } else if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                color = GREEN;
            } else {
                color = BLUE;
            }
            return color + formatMessage(record) + RESET + "\n";
        }
    }

}
