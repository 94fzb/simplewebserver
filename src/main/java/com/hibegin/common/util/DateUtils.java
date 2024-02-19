package com.hibegin.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateUtils {

    public static String toGMTString(Date date){
        Instant instant = date.toInstant();
        // 将Instant转换为ZonedDateTime，指定GMT时区
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.of("GMT"));
        // 创建一个DateTimeFormatter并指定为Cookie过期日期的格式
        DateTimeFormatter cookieExpireFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.ENGLISH);
        // 使用DateTimeFormatter格式化日期时间为Cookie的过期日期格式
        return zonedDateTime.format(cookieExpireFormatter);
    }

    public static void main(String[] args) {
        String gmt = toGMTString(new Date());
        System.out.println("gmt = " + gmt);
    }
}
