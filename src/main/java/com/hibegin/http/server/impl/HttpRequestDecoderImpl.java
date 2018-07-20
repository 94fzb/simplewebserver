package com.hibegin.http.server.impl;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.execption.RequestBodyTooLargeException;
import com.hibegin.http.server.execption.UnSupportMethodException;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.util.FileCacheKit;

import java.io.*;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestDecoderImpl implements HttpRequestDeCoder {

    private static final String CRLF = "\r\n";
    static final String SPLIT = CRLF + CRLF;
    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestDecoderImpl.class);
    private final SimpleHttpRequest request;
    private int dataLength;
    private byte[] headerBytes = new byte[]{};

    public HttpRequestDecoderImpl(RequestConfig requestConfig, ApplicationContext applicationContext, ReadWriteSelectorHandler handler) {
        this.request = new SimpleHttpRequest(handler, applicationContext, requestConfig);
    }

    @Override
    public Map.Entry<Boolean, ByteBuffer> doDecode(ByteBuffer byteBuffer) throws Exception {
        Map.Entry<Boolean, ByteBuffer> result;
        //HTTPS proxy
        if (request.requestHeaderStr != null && request.getMethod() == HttpMethod.CONNECT) {
            result = new AbstractMap.SimpleEntry<>(true, ByteBuffer.allocate(0));
            saveRequestBodyBytes(byteBuffer.array());
        } else {
            // 存在2种情况,提交的数据一次性读取完成,提交的数据一次性读取不完
            boolean flag;
            if (getRequestBodyBytes() == null) {
                headerBytes = BytesUtil.mergeBytes(headerBytes, byteBuffer.array());
                String fullDataStr = new String(headerBytes);
                parseHttpMethod();
                if (fullDataStr.contains(SPLIT)) {
                    String httpHeader = fullDataStr.substring(0, fullDataStr.indexOf(SPLIT));
                    request.requestHeaderStr = httpHeader;
                    String headerArr[] = httpHeader.split(CRLF);
                    // parse HttpHeader
                    parseHttpProtocolHeader(headerArr);
                    int headerByteLength = httpHeader.getBytes().length + SPLIT.getBytes().length;
                    byte[] requestBody;
                    if (headerBytes.length - headerByteLength > 0) {
                        requestBody = BytesUtil.subBytes(headerBytes, headerByteLength, headerBytes.length - headerByteLength);
                    } else {
                        requestBody = new byte[0];
                    }
                    flag = parseHttpRequestBody(requestBody);
                    //处理完成，清空byte[]
                    headerBytes = new byte[]{};
                    if (isNeedEmptyRequestBody()) {
                        result = new AbstractMap.SimpleEntry<>(flag, ByteBuffer.wrap(requestBody));
                    } else {
                        result = new AbstractMap.SimpleEntry<>(flag, ByteBuffer.allocate(0));
                    }
                } else {
                    result = new AbstractMap.SimpleEntry<>(false, ByteBuffer.allocate(0));
                }
            } else {
                flag = saveRequestBodyBytes(byteBuffer.array());
                if (flag) {
                    dealRequestBodyData();
                }
                result = new AbstractMap.SimpleEntry<>(flag, ByteBuffer.allocate(0));
            }
        }
        return result;
    }

    /**
     * 使用磁盘文件替代内存 ByteBuffered，避免OOM
     *
     * @param bytes
     * @return
     * @throws IOException
     */
    private boolean saveRequestBodyBytes(byte[] bytes) throws IOException {
        if (bytes.length > 0) {
            if (request.tmpRequestBodyFile != null) {
                try (FileOutputStream fileOutputStream = new FileOutputStream(request.tmpRequestBodyFile, true)) {
                    fileOutputStream.write(bytes);
                }
            } else {
                //first record use full request buffer
                request.tmpRequestBodyFile = FileCacheKit.generatorRequestTempFile(request.getServerConfig().getPort(), bytes);
            }
            return request.tmpRequestBodyFile.length() == dataLength;
        }
        return false;
    }

    private byte[] getRequestBodyBytes() throws FileNotFoundException {
        if (request.tmpRequestBodyFile != null) {
            return IOUtil.getByteByInputStream(new FileInputStream(request.tmpRequestBodyFile));
        } else {
            return null;
        }
    }

    private void parseHttpMethod() throws UnSupportMethodException {
        if (request.method == null) {
            boolean check = false;
            String requestLine = new String(headerBytes);
            for (HttpMethod httpMethod : HttpMethod.values()) {
                if (requestLine.startsWith(httpMethod.name() + " ")) {
                    request.method = httpMethod;
                    check = true;
                    break;
                }
            }
            if (!check) {
                throw new UnSupportMethodException(requestLine);
            }
        }
    }

    private boolean parseHttpRequestBody(byte[] requestBodyData) throws IOException {
        parseUrlEncodedStrToMap(request.queryStr);
        if (isNeedEmptyRequestBody()) {
            return true;
        } else {
            Object contentLengthObj = request.getHeader("Content-Length");
            if (contentLengthObj != null) {
                dataLength = Integer.parseInt(contentLengthObj.toString());
                if (dataLength == 0) {
                    return true;
                }
                if (dataLength > getRequest().getRequestConfig().getMaxRequestBodySize()) {
                    throw new RequestBodyTooLargeException("The Content-Length outside the max upload size " + ConfigKit.getMaxRequestBodySize());
                }
                if (saveRequestBodyBytes(requestBodyData)) {
                    dealRequestBodyData();
                    return true;
                } else {
                    return false;
                }
            } else {
                return true;
            }
        }
    }

    /**
     * 存在 content-length 认为
     *
     * @return
     */
    private boolean isNeedEmptyRequestBody() {
        return request.getHeader("Content-Length") == null && (request.method == HttpMethod.GET || request.method == HttpMethod.CONNECT || request.method == HttpMethod.TRACE);
    }

    private void parseHttpProtocolHeader(String[] headerArr) throws Exception {
        String pHeader = headerArr[0];
        String[] protocolHeaderArr = pHeader.split(" ");
        String tUrl = request.uri = protocolHeaderArr[1];
        // just for some proxy-client
        if (tUrl.startsWith(request.getScheme() + "://")) {
            tUrl = tUrl.substring((request.getScheme() + "://").length());
            if (tUrl.contains("/")) {
                request.header.put("Host", tUrl.substring(0, tUrl.indexOf("/")));
                tUrl = tUrl.substring(tUrl.indexOf("/"));
            } else {
                request.header.put("Host", tUrl);
                tUrl = "/";
            }
        }
        if (tUrl.contains("?")) {
            request.uri = tUrl.substring(0, tUrl.indexOf("?"));
            request.queryStr = tUrl.substring(tUrl.indexOf("?") + 1);
        } else {
            request.uri = tUrl;
        }
        if (request.uri.contains("/")) {
            request.uri = URLDecoder.decode(request.uri.substring(request.uri.indexOf("/")), request.getRequestConfig().getCharSet());
        } else {
            request.getHeaderMap().put("Host", request.uri);
            request.uri = "/";
        }
        // 先得到请求头信息
        for (int i = 1; i < headerArr.length; i++) {
            dealRequestHeaderString(headerArr[i]);
        }
    }

    private void dealRequestHeaderString(String str) {
        if (str.contains(":")) {
            request.header.put(str.split(":")[0], str.substring(str.indexOf(":") + 1).trim());
        }
    }


    private void parseUrlEncodedStrToMap(String queryString) {
        if (request.paramMap == null) {
            request.paramMap = new HashMap<>();
        }
        if (queryString != null) {
            Map<String, List<String>> tempParam = new HashMap<>();
            String args[] = queryString.split("&");
            for (String string : args) {
                int idx = string.indexOf("=");
                if (idx != -1) {
                    String key = string.substring(0, idx);
                    String value = string.substring(idx + 1);
                    if (tempParam.containsKey(key)) {
                        tempParam.get(key).add(value);
                    } else {
                        List<String> paramValues = new ArrayList<>();
                        paramValues.add(value);
                        tempParam.put(key, paramValues);
                    }
                }
            }
            for (Entry<String, List<String>> entry : tempParam.entrySet()) {
                request.paramMap.put(entry.getKey(), entry.getValue().toArray(new String[entry.getValue().size()]));
            }
        }
    }

    private void dealRequestBodyData() throws FileNotFoundException {
        byte[] requestBody = getRequestBodyBytes();
        if (requestBody != null) {
            if (request.getHeader("Content-Type") != null) {
                String contentType = request.getHeader("Content-Type").split(";")[0];
                //FIXME 不支持多文件上传，不支持这里有其他属性字段
                if ("multipart/form-data".equals(contentType)) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader bin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(requestBody)))) {
                        String headerStr;
                        while ((headerStr = bin.readLine()) != null && !"".equals(headerStr)) {
                            sb.append(headerStr).append(CRLF);
                            dealRequestHeaderString(headerStr);
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "", e);
                    }
                    String contentDisposition = request.getHeader("Content-Disposition");
                    if (contentDisposition != null) {
                        String inputName = contentDisposition.split(";")[1].split("=")[1].replace("\"", "");
                        int length1 = sb.toString().split(CRLF)[0].getBytes().length + CRLF.getBytes().length;
                        int length2 = sb.toString().getBytes().length + 2;
                        int dataLength = requestBody.length - length1 - length2 - SPLIT.getBytes().length;
                        File file = FileCacheKit.generatorRequestTempFile(request.getServerConfig().getPort(), BytesUtil.subBytes(requestBody, length2, dataLength));
                        if (request.files == null) {
                            request.files = new HashMap<>();
                        }
                        request.files.put(inputName, file);
                    }
                } else if ("application/x-www-form-urlencoded".equals(contentType)) {
                    parseUrlEncodedStrToMap(new String(requestBody));
                }
            } else {
                parseUrlEncodedStrToMap(new String(requestBody));
            }
        }
    }

    @Override
    public HttpRequest getRequest() {
        return request;
    }
}