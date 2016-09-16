package com.hibegin.http.server.util;

import java.util.HashMap;
import java.util.Map;

public class StatusCodeUtil {

    private static Map<Integer, String> map = new HashMap<>();

    static {
        map.put(100, "Continue");
        map.put(101, "Switching Protocols");
        map.put(200, "OK");
        map.put(201, "Created");
        map.put(202, "Accepted");
        map.put(203, "Non-Authoritative Information");
        map.put(204, "No Content");
        map.put(205, "Reset Content");
        map.put(206, "Partial Content");
        map.put(300, "Multiple Choices");
        map.put(301, "Moved Permanently");
        map.put(302, "Found");
        map.put(303, "See Other");
        map.put(304, "Not Modified");
        map.put(305, "Use Proxy");
        map.put(307, "Temporary Redirect");
        map.put(400, "Bad Request");
        map.put(401, "Unauthorized");
        map.put(402, "Payment Required");
        map.put(403, "Forbidden");
        map.put(404, "Not Found");
        map.put(405, "Method Not Allowed");
        map.put(406, "Not Acceptable");
        map.put(407, "Proxy Authentication Required");
        map.put(408, "Request Timeout");
        map.put(409, "Conflict");
        map.put(410, "Gone");
        map.put(411, "Length Required");
        map.put(412, "Precondition Failed");
        map.put(413, "Payload Too Large");
        map.put(414, "URI Too Long");
        map.put(415, "Unsupported Media Type");
        map.put(416, "Range Not Satisfiable");
        map.put(417, "Expectation Failed");
        map.put(426, "Upgrade Required");
        map.put(500, "Internal Server Error");
        map.put(501, "Not Implemented");
        map.put(502, "Bad Gateway");
        map.put(503, "Service Unavailable");
        map.put(504, "Gateway Timeout");
        map.put(505, "HTTP Version Not Supported");
    }

    public static String getStatusCode(Integer code) {
        return map.get(code);
    }
}
