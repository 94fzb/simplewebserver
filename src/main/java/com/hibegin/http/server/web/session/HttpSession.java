package com.hibegin.http.server.web.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HttpSession {

    private String sessionId;

    private Map<String, Object> attrMap = Collections.synchronizedMap(new HashMap<String, Object>());

    public HttpSession(String sessionID) {
        this.sessionId = sessionID;
    }

    public void setAttr(String name, Object value) {
        attrMap.put(name, value);
    }

    public String getSessionId() {
        return sessionId;
    }

    public Object getAttr(String name) {
        return attrMap.get(name);
    }

    public void removeAttr(String name) {
        attrMap.remove(name);
    }

    public void invalidate() {
        SessionUtil.sessionMap.remove(sessionId);
    }

    public Map getAttrMap() {
        return attrMap;
    }
}
