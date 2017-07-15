package com.hibegin.http.server.util;

public class StringsUtil {

    public static String getHtmlStrByStatusCode(int statusCode) {
        return "<html><head><title>" + statusCode + " " + StatusCodeUtil.getStatusCodeDesc(statusCode) + "</title></head><body><center><h1>" + statusCode + " " + StatusCodeUtil.getStatusCodeDesc(statusCode) + "</h1></center><hr><center>" + ServerInfo.getName() + "/" + ServerInfo.getVersion() + "</center></body></html>";
    }
}
