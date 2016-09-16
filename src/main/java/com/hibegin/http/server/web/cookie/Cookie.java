package com.hibegin.http.server.web.cookie;


public class Cookie {

    public static String JSESSIONID = "JSESSIONID";
    private String name;
    private String value;
    private String domain;
    private String path = "/";
    private Integer maxAge;
    private boolean create;
    private boolean httpOnly;

    public Cookie(boolean create) {
        this.setCreate(create);
    }

    public Cookie() {
        //
    }

    public static Cookie[] saxToCookie(String cookieStr) {
        String[] kvArr = cookieStr.split(";");
        Cookie[] cookies = new Cookie[kvArr.length];
        for (int i = 0; i < kvArr.length; i++) {
            String[] kv = kvArr[i].trim().split("=");
            Cookie cookie = new Cookie();
            cookie.setName(kv[0]);
            cookie.setValue(kv[1]);
            cookies[i] = cookie;
        }
        return cookies;
    }

    public static String getJSessionId(String cookieStr) {
        String[] kvArr = cookieStr.split(";");
        for (String aKvArr : kvArr) {
            String[] kv = aKvArr.trim().split("=");
            if (JSESSIONID.equals(kv[0])) {
                return (kv[1]);
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        String cookieStr;
        if (maxAge == null) {
            cookieStr = name + "=" + value + ";" + "Path=" + path;
        } else {
            cookieStr = name + "=" + value + ";" + "Path=" + path + ";max-age=" + maxAge;
        }
        if (httpOnly) {
            cookieStr += ";HttpOnly";
        }
        return cookieStr;
    }

    public boolean isCreate() {
        return create;
    }

    public void setCreate(boolean create) {
        this.create = create;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }
}
