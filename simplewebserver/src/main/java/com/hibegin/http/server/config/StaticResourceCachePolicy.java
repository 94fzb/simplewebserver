package com.hibegin.http.server.config;

/**
 * Cache policy for static resources.
 *
 * <p>Caching is disabled by default. {@link #REVALIDATE} allows a browser to
 * store the response, but requires it to validate the resource before reuse.</p>
 */
public enum StaticResourceCachePolicy {

    DISABLED(null),
    REVALIDATE("no-cache");

    private final String cacheControl;

    StaticResourceCachePolicy(String cacheControl) {
        this.cacheControl = cacheControl;
    }

    public boolean isEnabled() {
        return cacheControl != null;
    }

    public String getCacheControl() {
        return cacheControl;
    }
}
