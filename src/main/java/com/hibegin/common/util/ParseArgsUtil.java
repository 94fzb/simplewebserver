package com.hibegin.common.util;

public class ParseArgsUtil {

    public static boolean justTips(String[] args, String processName, String version) {
        if (args.length > 0) {
            switch (args[0]) {
                case "-v":
                case "--version":
                    System.out.println(processName + " version: " + version);
                    return true;
                case "--properties":
                    System.getProperties().forEach((key, value) -> System.out.format("%s=%s%n", key, value));
                    return true;
                case "--env":
                    System.getenv().forEach((key, value) -> System.out.format("%s=%s%n", key, value));
                    return true;
            }
        }
        return false;
    }
}
