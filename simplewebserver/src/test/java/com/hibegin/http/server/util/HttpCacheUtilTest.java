package com.hibegin.http.server.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpCacheUtilTest {

    @Test
    public void ifNoneMatchTakesPrecedenceAndSupportsWeakComparison() {
        String entityTag = HttpCacheUtil.buildWeakEntityTag(1_700_000_000_000L, 128L);

        assertTrue(HttpCacheUtil.isNotModified("\"other\", " + entityTag,
                "Wed, 01 Jan 2020 00:00:00 GMT", entityTag, 1_700_000_000_000L));
        assertTrue(HttpCacheUtil.isNotModified("*", null, entityTag, 1_700_000_000_000L));
        assertFalse(HttpCacheUtil.isNotModified("\"other\"",
                "Wed, 01 Jan 2030 00:00:00 GMT", entityTag, 1_700_000_000_000L));
    }

    @Test
    public void ifModifiedSinceUsesHttpSecondPrecision() {
        long lastModified = 1_700_000_000_789L;
        String sameSecond = HttpCacheUtil.formatHttpDate(lastModified);

        assertTrue(HttpCacheUtil.isNotModified(null, sameSecond,
                HttpCacheUtil.buildWeakEntityTag(lastModified, 1L), lastModified));
        assertFalse(HttpCacheUtil.isNotModified(null, "invalid",
                HttpCacheUtil.buildWeakEntityTag(lastModified, 1L), lastModified));
    }
}
