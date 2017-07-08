package com.hibegin.common.util;

import java.io.File;

public class EnvKit {

    public static void savePid(String pidFile) {
        long pid = ProcessHandle.current().pid();
        File file = new File(pidFile);
        if (file.exists()) {
            file.delete();
        }
        IOUtil.writeStrToFile(pid + "", file);
        file.deleteOnExit();
    }
}
