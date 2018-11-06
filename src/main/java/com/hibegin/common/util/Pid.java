package com.hibegin.common.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Pid {

    public static long get() {
        try {
            //兼容java7
            if (Class.forName("java.lang.management.ManagementFactory") != null) {
                Object runtimeMXBean = Class.forName("java.lang.management.ManagementFactory").getMethod("getRuntimeMXBean").invoke(null);
                Method method = runtimeMXBean.getClass().getMethod("getName");
                method.setAccessible(true);
                return Long.valueOf(((String) method.invoke(runtimeMXBean)).split("@")[0]);
            }
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
