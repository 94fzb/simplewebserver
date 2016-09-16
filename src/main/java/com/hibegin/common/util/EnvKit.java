package com.hibegin.common.util;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class EnvKit {

    public static void savePid(String pidFile) {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        int pid = Integer.valueOf(runtimeMXBean.getName().split("@")[0]);
        File file = new File(pidFile);
        if (file.exists()) {
            file.delete();
        }
        IOUtil.writeStrToFile(pid + "", file);
        file.deleteOnExit();
    }
}
