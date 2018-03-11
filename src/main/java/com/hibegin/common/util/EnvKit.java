package com.hibegin.common.util;

import java.io.File;

public class EnvKit {

    private static final boolean android;

    static {
        boolean tmpFlag;
        try {
            Class.forName("android.app.Application");
            tmpFlag = true;
        } catch (ClassNotFoundException e) {
            tmpFlag = false;
        }
        android = tmpFlag;
    }

    public static void savePid(String pidFile) {
        long pid = ProcessHandle.current().pid();
        File file = new File(pidFile);
        if (file.exists()) {
            file.delete();
        }
        IOUtil.writeStrToFile(pid + "", file);
        file.deleteOnExit();
    }

    public static boolean isAndroid() {
        return android;
    }
}
