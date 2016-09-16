package com.hibegin.http.server.web.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionUtil {

    public static Map<String, HttpSession> sessionMap = new ConcurrentHashMap<String, HttpSession>();

    public static HttpSession getSessionById(String sessionID) {
        return sessionMap.get(sessionID);
    }
}
