package com.hibegin.http.server.impl;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.web.cookie.Cookie;
import com.hibegin.http.server.execption.ContentLengthTooLargeException;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.web.session.HttpSession;
import com.hibegin.http.server.web.session.SessionUtil;
import com.hibegin.http.server.util.PathUtil;

import java.io.*;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

public class HttpRequestDecoderImpl implements HttpRequestDeCoder {

    protected static final String CRLF = "\r\n";
    protected static final String split = CRLF + CRLF;
    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestDecoderImpl.class);
    private SimpleHttpRequest request;


    public HttpRequestDecoderImpl(SocketAddress socketAddress, RequestConfig requestConfig, ServerContext serverContext, ReadWriteSelectorHandler handler) {
        this.request = new SimpleHttpRequest(System.currentTimeMillis(), handler, serverContext);
        this.request.requestConfig = requestConfig;
        this.request.ipAddr = socketAddress;
        if (requestConfig.isSsl()) {
            request.scheme = "https";
        }
    }

    private static String randomFile() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        return df.format(new Date()) + "_" + new Random().nextInt(1000);
    }

    @Override
    public boolean doDecode(byte[] data) throws Exception {
        boolean flag = false;
        if (request.dataBuffer == null) {
            request.headerSb.append(new String(data));
            if (request.headerSb.toString().contains(split)) {
                String fullData = request.headerSb.toString();
                String httpHeader = fullData.substring(0, fullData.indexOf(split));
                String headerArr[] = httpHeader.split(CRLF);
                String pHeader = headerArr[0];
                if (!"".equals(pHeader.split(" ")[0])) {
                    // parse HttpHeader
                    parseHttpProtocolHeader(headerArr, pHeader);
                    flag = parseHttpMethod(data, httpHeader);
                }
            }
        } else {
            request.dataBuffer.put(data);
            flag = !request.dataBuffer.hasRemaining();
            if (flag) {
                dealPostData();
            }
        }
        return flag;
    }

    private boolean parseHttpMethod(byte[] data, String httpHeader) {
        boolean flag = false;

        if (request.method == HttpMethod.GET || request.method == HttpMethod.CONNECT) {
            wrapperParamStrToMap(request.queryStr);
            flag = true;
        }
        // 存在2种情况
        // 1,POST 提交的数据一次性读取完成。
        // 2,POST 提交的数据一次性读取不完。
        else if (request.method == HttpMethod.POST || request.method == HttpMethod.DELETE || request.method == HttpMethod.PUT) {
            wrapperParamStrToMap(request.queryStr);
            if (request.header.get("Content-Length") != null) {
                Integer dateLength = Integer.parseInt(request.header.get("Content-Length"));
                if (dateLength > ConfigKit.getMaxUploadSize()) {
                    throw new ContentLengthTooLargeException("Content-Length outSide the max uploadSize "
                            + ConfigKit.getMaxUploadSize());
                }
                request.dataBuffer = ByteBuffer.allocate(dateLength);
                int headerLength = httpHeader.getBytes().length + split.getBytes().length;
                byte[] remain = BytesUtil.subBytes(data, headerLength, data.length - headerLength);
                request.dataBuffer.put(remain);
                flag = !request.dataBuffer.hasRemaining();
                if (flag) {
                    dealPostData();
                }
            } else {
                flag = true;
            }

        }
        if (!request.requestConfig.isDisableCookie()) {
            // deal with cookie
            dealWithCookie();
        }
        return flag;
    }

    private void parseHttpProtocolHeader(String[] headerArr, String pHeader) throws Exception {
        try {
            request.method = HttpMethod.valueOf(pHeader.split(" ")[0]);
        } catch (IllegalArgumentException e) {
            String msg = "unSupport method " + pHeader.split(" ")[0];
            LOGGER.warning(msg);
            throw new Exception(msg);
        }
        // 先得到请求头信息
        for (int i = 1; i < headerArr.length; i++) {
            dealRequestHeaderString(headerArr[i]);
        }
        String tUrl = request.uri = pHeader.split(" ")[1];
        // just for some proxy-client
        if (tUrl.startsWith(request.scheme + "://")) {
            tUrl = tUrl.substring((request.scheme + "://").length());
            request.header.put("Host", tUrl.substring(0, tUrl.indexOf("/")));
            tUrl = tUrl.substring(tUrl.indexOf("/"));
        }
        if (tUrl.contains("?")) {
            request.uri = tUrl.substring(0, tUrl.indexOf("?"));
            request.queryStr = tUrl.substring(tUrl.indexOf("?") + 1);
        } else {
            request.uri = tUrl;
        }
        if (request.uri.contains("/")) {
            request.uri = URLDecoder.decode(request.uri.substring(request.uri.indexOf("/")), "UTF-8");
        } else {
            request.getHeaderMap().put("Host", request.uri);
            request.uri = "/";
        }
    }

    private void dealRequestHeaderString(String str) {
        if (str.contains(":")) {
            request.header.put(str.split(":")[0], str.substring(str.indexOf(":") + 1).trim());
        }
    }

    private void dealWithCookie() {
        boolean createCookie = true;
        if (request.header.get("Cookie") != null) {
            request.cookies = Cookie.saxToCookie(request.header.get("Cookie"));
            String jsessionid = Cookie.getJSessionId(request.header.get("Cookie"));
            if (jsessionid == null) {
                Cookie[] tCookies = new Cookie[request.cookies.length + 1];
                // copy cookie
                System.arraycopy(request.cookies, 0, tCookies, 0, request.cookies.length);
                request.cookies = tCookies;
            } else {
                request.session = SessionUtil.getSessionById(jsessionid);
                if (request.session != null) {
                    createCookie = false;
                }
            }
        }
        if (createCookie) {
            if (request.cookies == null) {
                request.cookies = new Cookie[1];
            }
            Cookie cookie = new Cookie(true);
            String jsessionid = UUID.randomUUID().toString();
            cookie.setName(Cookie.JSESSIONID);
            cookie.setPath("/");
            cookie.setValue(jsessionid);
            request.cookies[request.cookies.length - 1] = cookie;
            request.session = new HttpSession(jsessionid);
            SessionUtil.sessionMap.put(jsessionid, request.session);
            LOGGER.info("create a Cookie " + cookie.toString());
        }
    }

    public void wrapperParamStrToMap(String paramStr) {
        request.paramMap = new HashMap<>();
        if (paramStr != null) {
            Map<String, Set<String>> tempParam = new HashMap<>();
            String args[] = paramStr.split("&");
            for (String string : args) {
                int idx = string.indexOf("=");
                if (idx != -1) {
                    String key = string.substring(0, idx);
                    String value = string.substring(idx + 1);
                    if (tempParam.containsKey(key)) {
                        tempParam.get(key).add(value);
                    } else {
                        Set<String> tmpSet = new TreeSet<>();
                        tmpSet.add(value);
                        tempParam.put(key, tmpSet);
                    }
                }
            }
            for (Entry<String, Set<String>> ent : tempParam.entrySet()) {
                request.paramMap.put(ent.getKey(), ent.getValue().toArray(new String[ent.getValue().size()]));
            }
        }
    }

    private void dealPostData() {
        if (request.header.get("Content-Type") != null && request.header.get("Content-Type").split(";")[0] != null) {
            if ("multipart/form-data".equals(request.header.get("Content-Type").split(";")[0])) {
                //TODO 使用合理算法提高对网卡的利用率
                //FIXME 不支持多文件上传，不支持这里有其他属性字段
                if (!request.dataBuffer.hasRemaining()) {
                    BufferedReader bin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(request.dataBuffer.array())));
                    //ByteArrayOutputStream bout=new ByteArrayOutputStream(d);
                    StringBuilder sb2 = new StringBuilder();
                    try {
                        String headerStr;
                        while ((headerStr = bin.readLine()) != null && !"".equals(headerStr)) {
                            sb2.append(headerStr).append(CRLF);
                            dealRequestHeaderString(headerStr);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            bin.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    LOGGER.info(request.header.toString());

                    String inputName = request.header.get("Content-Disposition").split(";")[1].split("=")[1].replace("\"", "");
                    String fileName;
                    if (request.header.get("Content-Disposition").split(";").length > 2) {
                        fileName = request.header.get("Content-Disposition").split(";")[2].split("=")[1].replace("\"", "");
                    } else {
                        fileName = randomFile();
                    }
                    File file = new File(PathUtil.getTempPath() + fileName);
                    request.files.put(inputName, file);
                    int length1 = sb2.toString().split(CRLF)[0].getBytes().length + CRLF.getBytes().length;
                    int length2 = sb2.toString().getBytes().length + 2;
                    int dataLength = Integer.parseInt(request.header.get("Content-Length")) - length1 - length2 - split.getBytes().length;
                    IOUtil.writeBytesToFile(BytesUtil.subBytes(request.dataBuffer.array(), length2, dataLength), file);
                    request.paramMap = new HashMap<>();

                }
            } else {
                wrapperParamStrToMap(new String(request.dataBuffer.array()));
            }
        } else {
            wrapperParamStrToMap(new String(request.dataBuffer.array()));
        }
    }

    @Override
    public SimpleHttpRequest getRequest() {
        return request;
    }
}
