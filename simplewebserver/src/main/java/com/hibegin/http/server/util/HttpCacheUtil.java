package com.hibegin.http.server.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class HttpCacheUtil {

    private HttpCacheUtil() {
    }

    public static String buildWeakEntityTag(long lastModified, long contentLength) {
        return "W/\"" + Long.toHexString(lastModified) + "-" + Long.toHexString(contentLength) + "\"";
    }

    public static String formatHttpDate(long time) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC));
    }

    public static boolean isNotModified(String ifNoneMatch, String ifModifiedSince,
                                        String entityTag, long lastModified) {
        if (ifNoneMatch != null && !ifNoneMatch.trim().isEmpty()) {
            return matchesIfNoneMatch(ifNoneMatch, entityTag);
        }
        return isNotModifiedSince(ifModifiedSince, lastModified);
    }

    static boolean matchesIfNoneMatch(String ifNoneMatch, String entityTag) {
        if (ifNoneMatch == null || entityTag == null) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String value = candidate.trim();
            if ("*".equals(value) || stripWeakPrefix(value).equals(stripWeakPrefix(entityTag))) {
                return true;
            }
        }
        return false;
    }

    static boolean isNotModifiedSince(String ifModifiedSince, long lastModified) {
        if (ifModifiedSince == null || ifModifiedSince.trim().isEmpty() || lastModified <= 0) {
            return false;
        }
        try {
            long conditionTime = ZonedDateTime.parse(
                    ifModifiedSince, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            return lastModified / 1000 <= conditionTime / 1000;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String stripWeakPrefix(String entityTag) {
        if (entityTag.regionMatches(true, 0, "W/", 0, 2)) {
            return entityTag.substring(2);
        }
        return entityTag;
    }
}
