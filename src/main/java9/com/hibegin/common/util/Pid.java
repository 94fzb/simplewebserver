package com.hibegin.common.util;

public class Pid {

    public static long get() {
        return ProcessHandle.current().pid();
    }

}
