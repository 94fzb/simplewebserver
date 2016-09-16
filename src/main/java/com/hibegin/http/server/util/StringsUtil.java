package com.hibegin.http.server.util;

public class StringsUtil {

    public static String getHtmlStrByStatusCode(int statusCode) {
        return "<html><head><title>" + statusCode + " " + StatusCodeUtil.getStatusCode(statusCode) + "</title></head><body><center><h1>" + statusCode + " " + StatusCodeUtil.getStatusCode(statusCode) + "</h1></center><hr><center>" + ServerInfo.getName() + "/" + ServerInfo.getVersion() + "</center></body></html>";
    }
}
