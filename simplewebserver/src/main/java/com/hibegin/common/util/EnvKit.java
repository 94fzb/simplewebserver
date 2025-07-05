package com.hibegin.common.util;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class EnvKit {

    private static final boolean ANDROID;

    static {
        boolean tmpFlag;
        try {
            Class.forName("android.app.Application");
            tmpFlag = true;
        } catch (ClassNotFoundException e) {
            tmpFlag = false;
        }
        ANDROID = tmpFlag;
    }

    public static void savePid(String pidFile) {
        File file = new File(pidFile);
        if (file.exists()) {
            file.delete();
        }
        long pid = Pid.get();
        if (pid > 0) {
            IOUtil.writeStrToFile(pid + "", file);
            file.deleteOnExit();
        }
    }

    public static boolean isAndroid() {
        return ANDROID;
    }

    public static boolean isLambda() {
        String value = System.getenv("AWS_LAMBDA_FUNCTION_NAME");
        return Objects.nonNull(value);
    }

    public static boolean isDevMode() {
        if (Objects.equals(System.getenv("DEV_MODE"), "true")) {
            return true;
        }
        return Objects.equals(System.getProperty("sws.run.mode"), "dev");
    }

    public static boolean isDebugMode() {
        if (Objects.equals(System.getenv("DEBUG_MODE"), "true")) {
            return true;
        }
        return Objects.equals(System.getProperty("sws.run.mode"), "debug");
    }

    public static String getHostName() {
        try {
            // try InetAddress.LocalHost first;
            // NOTE -- InetAddress.getLocalHost().getHostName() will not work in certain environments.
            try {
                String result = InetAddress.getLocalHost().getHostName();
                if (Objects.nonNull(result) && !result.isEmpty()) return result;
            } catch (UnknownHostException e) {
                // failed;  try alternate means.
            }

            String host = System.getenv("COMPUTERNAME");
            if (host != null) {
                return host;
            }
            host = System.getenv("HOSTNAME");
            return ObjectUtil.requireNonNullElse(host, "unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }


    public static File savePidBySystemEnvKey(String key) {
        String pidFile = System.getenv().get(key);
        if (pidFile != null && !pidFile.isEmpty()) {
            //save pid
            EnvKit.savePid(pidFile);
            File pFile = new File(pidFile);
            pFile.deleteOnExit();
            return pFile;
        }
        return null;
    }

    public static void saveHttpPortToFile(String envKey, Integer port) {
        String filePath = System.getenv().get(envKey);
        if (Objects.isNull(filePath)) {
            return;
        }
        File file = new File(filePath);
        if (Objects.nonNull(port) && port > 0) {
            IOUtil.writeStrToFile(String.valueOf(port), file);
        }
    }
}
