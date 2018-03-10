package com.hibegin.http.server.impl;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpRequestDeCoder;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.execption.ContentLengthTooLargeException;
import com.hibegin.http.server.execption.UnSupportMethodException;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.util.FileCacheKit;
import com.hibegin.http.server.util.PathUtil;

import java.io.*;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestDecoderImpl implements HttpRequestDeCoder {

    private static final String CRLF = "\r\n";
    static final String SPLIT = CRLF + CRLF;
    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestDecoderImpl.class);
    private SimpleHttpRequest request;
    private boolean headerHandled = false;
    private byte[] headerBytes = new byte[]{};

    public HttpRequestDecoderImpl(RequestConfig requestConfig, ServerContext serverContext, ReadWriteSelectorHandler handler) {
        this.request = new SimpleHttpRequest(handler, serverContext, requestConfig);
    }

    private static String randomFile() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        return df.format(new Date()) + "_" + new Random().nextInt(1000);
    }

    @Override
    public Map.Entry<Boolean, ByteBuffer> doDecode(ByteBuffer byteBuffer) throws Exception {
        Map.Entry<Boolean, ByteBuffer> result;
        if (headerHandled && request.getMethod() == HttpMethod.CONNECT) {
            if (request.requestBodyBuffer == null) {
                request.requestBodyBuffer = byteBuffer;
            } else {
                request.requestBodyBuffer = ByteBuffer.wrap(BytesUtil.mergeBytes(IOUtil.getByteByInputStream(new FileInputStream(request.requestBodyFile)), byteBuffer.array()));
            }
            result = new AbstractMap.SimpleEntry<>(true, ByteBuffer.allocate(0));
        } else {
            // 存在2种情况
            // 1,提交的数据一次性读取完成。
            // 2,提交的数据一次性读取不完。
            boolean flag;
            if (request.requestBodyBuffer == null) {
                headerBytes = BytesUtil.mergeBytes(headerBytes, byteBuffer.array());
                String fullDataStr = new String(headerBytes);
                if (fullDataStr.contains(SPLIT)) {
                    headerHandled = true;
                    parseHttpMethod();
                    String httpHeader = fullDataStr.substring(0, fullDataStr.indexOf(SPLIT));
                    request.requestHeaderStr = httpHeader;
                    String headerArr[] = httpHeader.split(CRLF);
                    // parse HttpHeader
                    parseHttpProtocolHeader(headerArr);
                    int headerByteLength = httpHeader.getBytes().length + SPLIT.getBytes().length;
                    byte[] requestBody = BytesUtil.subBytes(headerBytes, headerByteLength, headerBytes.length - headerByteLength);
                    flag = parseHttpRequestBody(requestBody);
                    //处理完成，清空byte[]
                    headerBytes = new byte[]{};
                    if (isNeedEmptyRequestBody()) {
                        result = new AbstractMap.SimpleEntry<>(flag, ByteBuffer.wrap(requestBody));
                    } else {
                        result = new AbstractMap.SimpleEntry<>(flag, ByteBuffer.allocate(0));
                    }
                } else {
                    parseHttpMethod();
                    result = new AbstractMap.SimpleEntry<>(false, ByteBuffer.allocate(0));
                }
            } else {
                request.requestBodyBuffer.put(byteBuffer.array());
                flag = !request.requestBodyBuffer.hasRemaining();
                if (flag) {
                    dealRequestBodyData();
                }
                result = new AbstractMap.SimpleEntry<>(flag, ByteBuffer.allocate(0));
            }
        }
        if (result.getKey()) {
            if (!request.getRequestConfig().isRecordRequestBody()) {
                if (request.requestBodyBuffer != null) {
                    request.requestBodyBuffer.clear();
                }
            } else {
                if (request.requestBodyBuffer != null) {
                    if (request.requestBodyFile == null) {
                        request.requestBodyFile = FileCacheKit.generatorRequestTempFile(request.getServerConfig().getPort());
                    }
                    IOUtil.writeBytesToFile(request.requestBodyBuffer.array(), request.requestBodyFile);
                    request.requestBodyBuffer.clear();
                }
            }
        }
        return result;
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

    private boolean parseHttpRequestBody(byte[] requestBodyData) {
        boolean flag;
        if (isNeedEmptyRequestBody()) {
            wrapperParamStrToMap(request.queryStr);
            flag = true;
        } else {
            wrapperParamStrToMap(request.queryStr);
            Object contentLengthObj = request.getHeader("Content-Length");
            if (contentLengthObj != null) {
                Integer dateLength = Integer.parseInt(contentLengthObj.toString());
                if (dateLength > getRequest().getRequestConfig().getMaxRequestBodySize()) {
                    throw new ContentLengthTooLargeException("The Content-Length outside the max upload size " + ConfigKit.getMaxUploadSize());
                }
                request.requestBodyBuffer = ByteBuffer.allocate(dateLength);
                request.requestBodyBuffer.put(requestBodyData);
                flag = !request.requestBodyBuffer.hasRemaining();
                if (flag) {
                    dealRequestBodyData();
                }
            } else {
                flag = true;
            }
        }
        return flag;
    }

    private boolean isNeedEmptyRequestBody() {
        return request.method == HttpMethod.GET || request.method == HttpMethod.CONNECT || request.method == HttpMethod.TRACE;
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


    private void wrapperParamStrToMap(String paramStr) {
        if (request.paramMap == null) {
            request.paramMap = new HashMap<>();
        }
        if (paramStr != null) {
            Map<String, List<String>> tempParam = new HashMap<>();
            String args[] = paramStr.split("&");
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

    private void dealRequestBodyData() {
        if (request.header.get("Content-Type") != null && request.header.get("Content-Type").split(";")[0] != null) {
            //FIXME 不支持多文件上传，不支持这里有其他属性字段
            if ("multipart/form-data".equals(request.getHeader("Content-Type").split(";")[0])) {
                BufferedReader bin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(request.requestBodyBuffer.array())));
                StringBuilder sb = new StringBuilder();
                try {
                    String headerStr;
                    while ((headerStr = bin.readLine()) != null && !"".equals(headerStr)) {
                        sb.append(headerStr).append(CRLF);
                        dealRequestHeaderString(headerStr);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "", e);
                } finally {
                    try {
                        bin.close();
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "", e);
                    }
                }
                String contentDisposition = request.getHeader("Content-Disposition");
                if (contentDisposition != null) {
                    String inputName = contentDisposition.split(";")[1].split("=")[1].replace("\"", "");
                    String fileName;
                    if (contentDisposition.split(";").length > 2) {
                        fileName = contentDisposition.split(";")[2].split("=")[1].replace("\"", "");
                    } else {
                        fileName = randomFile();
                    }
                    File file = new File(PathUtil.getTempPath() + fileName);
                    if (request.files == null) {
                        request.files = new HashMap<>();
                    }
                    request.files.put(inputName, file);
                    int length1 = sb.toString().split(CRLF)[0].getBytes().length + CRLF.getBytes().length;
                    int length2 = sb.toString().getBytes().length + 2;
                    int dataLength = request.requestBodyBuffer.array().length - length1 - length2 - SPLIT.getBytes().length;
                    IOUtil.writeBytesToFile(BytesUtil.subBytes(request.requestBodyBuffer.array(), length2, dataLength), file);
                }
            } else {
                wrapperParamStrToMap(new String(request.requestBodyBuffer.array()));
            }
        } else {
            wrapperParamStrToMap(new String(request.requestBodyBuffer.array()));
        }
    }

    @Override
    public HttpRequest getRequest() {
        return request;
    }
}