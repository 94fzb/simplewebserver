package com.hibegin.common.util;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public class Pid {

    public static final Logger LOGGER = LoggerUtil.getLogger(Pid.class);

    public static long get() {
        try {
            //兼容java7
            if (Class.forName("java.lang.management.ManagementFactory") != null) {
                Object runtimeMXBean = Class.forName("java.lang.management.ManagementFactory").getMethod("getRuntimeMXBean").invoke(null);
                Method method = runtimeMXBean.getClass().getMethod("getName");
                method.setAccessible(true);
                return Long.parseLong(((String) method.invoke(runtimeMXBean)).split("@")[0]);
            }
        } catch (Exception e) {
            LOGGER.warning("get pid error " + e.getMessage());
        }
        return -1;
    }
}
