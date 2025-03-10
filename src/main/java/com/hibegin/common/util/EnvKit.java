package com.hibegin.common.util;

import java.io.File;
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

    public static boolean isDevMode() {
        return Objects.equals(System.getProperty("sws.run.mode"), "dev");
    }

    public static boolean isDebugMode() {
        return Objects.equals(System.getProperty("sws.run.mode"), "debug");
    }
}
