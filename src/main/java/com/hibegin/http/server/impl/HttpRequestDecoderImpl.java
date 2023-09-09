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
        // 存在2种情况,提交的数据一次性读取完成,提交的数据一次性读取不完
        if (Objects.isNull(getRequestBodyBytes())) {
            headerBytes = BytesUtil.mergeBytes(headerBytes, byteBuffer.array());
            String fullDataStr = new String(headerBytes);
            parseHttpMethod();
            if (fullDataStr.contains(SPLIT)) {
                String httpHeader = fullDataStr.substring(0, fullDataStr.indexOf(SPLIT));
                request.requestHeaderStr = httpHeader;
                String[] headerArr = httpHeader.split(CRLF);
                // parse HttpHeader
                parseProtocolHeader(headerArr);
                int headerByteLength = httpHeader.getBytes().length + SPLIT.getBytes().length;
                byte[] requestBody;
                if (headerBytes.length - headerByteLength > 0) {
                    requestBody = BytesUtil.subBytes(headerBytes, headerByteLength, headerBytes.length - headerByteLength);
                } else {
                    requestBody = new byte[0];
                }
                result = saveRequestBodyBytes(requestBody);
            } else {
                int maxHeaderSize = request.getRequestConfig().getMaxRequestHeaderSize();
                //没有读取到 SPLIT 时，检查 header 的最大长度
                if (headerBytes.length > maxHeaderSize) {
                    throw new RequestBodyTooLargeException("The http header to large " + headerBytes.length + " more than " + maxHeaderSize);
                }
                //没有 SPLIT，请求头部分不完整，需要继续等待，且已处理 byteBuffer，返回0
                result = new AbstractMap.SimpleEntry<>(false, ByteBuffer.allocate(0));
            }
        } else {
            result = saveRequestBodyBytes(byteBuffer.array());
        }
        if (Objects.equals(result.getKey(), true)) {
            dealRequestBodyData();
            //处理完成，清空byte[]
            headerBytes = new byte[]{};
            request.getHandler().flushRequestBB();
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
    private Map.Entry<Boolean, ByteBuffer> saveRequestBodyBytes(byte[] bytes) throws IOException {
        byte[] handleBytes = bytes;
        try {
            if (dataLength > 0) {
                handleBytes = BytesUtil.subBytes(bytes, 0, dataLength);
            }
            File tempFile = saveRequestBodyToTempFile(handleBytes);
            //requestBody full
            if (tempFile.exists() && tempFile.length() == dataLength) {
                int hasNextData = bytes.length - handleBytes.length;
                if (hasNextData > 0) {
                    byte[] nextData = BytesUtil.subBytes(bytes, handleBytes.length, hasNextData);
                    return new AbstractMap.SimpleEntry<>(true, ByteBuffer.wrap(nextData));
                } else {
                    return new AbstractMap.SimpleEntry<>(true, ByteBuffer.allocate(0));
                }
            } else {
                return new AbstractMap.SimpleEntry<>(dataLength == 0, ByteBuffer.allocate(0));
            }
        } finally {
            if (request.getApplicationContext().getServerConfig().getHttpRequestDecodeListener() != null) {
                request.getApplicationContext().getServerConfig().getHttpRequestDecodeListener().decodeRequestBodyBytesAfter(request, handleBytes);
            }
        }
    }

    private File saveRequestBodyToTempFile(byte[] handleBytes) throws IOException {
        File tempFile = request.tmpRequestBodyFile;
        if (Objects.nonNull(tempFile)) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(tempFile, true)) {
                fileOutputStream.write(handleBytes);
            }
        } else {
            tempFile = FileCacheKit.generatorRequestTempFile(request.getServerConfig().getPort() + "", handleBytes);
            request.tmpRequestBodyFile = tempFile;
        }
        return tempFile;
    }

    private byte[] getRequestBodyBytes() {
        if (request.tmpRequestBodyFile != null && request.tmpRequestBodyFile.exists()) {
            return IOUtil.getByteByFile(request.tmpRequestBodyFile);
        } else {
            return null;
        }
    }

    private void parseHttpMethod() {
        if (Objects.nonNull(request.method)) {
            return;
        }
        String requestLine = new String(headerBytes);
        for (HttpMethod httpMethod : HttpMethod.values()) {
            if (requestLine.startsWith(httpMethod.name() + " ")) {
                request.method = httpMethod;
                return;
            }
        }
        throw new UnSupportMethodException(requestLine.substring(0, Math.min(requestLine.length() - 1, 48)));
    }

    private void parseProtocolHeader(String[] headerArr) throws Exception {
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
        parseUrlEncodedStrToMap(request.queryStr);
        dataLength = Integer.parseInt(Objects.requireNonNullElse(request.getHeader("Content-Length"), "0"));
        if (dataLength > getRequest().getRequestConfig().getMaxRequestBodySize()) {
            throw new RequestBodyTooLargeException("The Content-Length outside the max upload size " + ConfigKit.getMaxRequestBodySize());
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
            String[] args = queryString.split("&");
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
                request.paramMap.put(entry.getKey(), entry.getValue().toArray(new String[0]));
            }
        }
    }

    public static String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex != -1 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return null;
    }

    private void dealRequestBodyData() throws IOException {
        byte[] requestBody = getRequestBodyBytes();
        if (Objects.isNull(requestBody)) {
            return;
        }
        String contentTypeHeader = request.getHeader("Content-Type");
        if (Objects.isNull(contentTypeHeader) || contentTypeHeader.trim().isEmpty()) {
            return;
        }
        String contentType = contentTypeHeader.split(";")[0];
        //FIXME 不支持多文件上传，不支持这里有其他属性字段
        if ("multipart/form-data".equals(contentType)) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader bin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(requestBody)))) {
                String headerStr;
                while ((headerStr = bin.readLine()) != null && !headerStr.isEmpty()) {
                    sb.append(headerStr).append(CRLF);
                    dealRequestHeaderString(headerStr);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
            String contentDisposition = request.getHeader("Content-Disposition");
            if (contentDisposition != null) {
                String[] kvs = contentDisposition.split(";");
                String inputKeyName = kvs[1].split("=")[1].replace("\"", "");
                String ext = null;
                if (kvs.length == 3) {
                    String inputFileName = kvs[2].split("=")[1].replace("\"", "");
                    ext = getFileExtension(inputFileName);
                }
                int length1 = sb.toString().split(CRLF)[0].getBytes().length + CRLF.getBytes().length;
                int length2 = sb.toString().getBytes().length + 2;
                int dataLength = requestBody.length - length1 - length2 - SPLIT.getBytes().length;
                File file = FileCacheKit.generatorRequestTempFile(request.getServerConfig().getPort() + (Objects.isNull(ext) ? "" : "." + ext), BytesUtil.subBytes(requestBody, length2, dataLength));
                if (request.files == null) {
                    request.files = new HashMap<>();
                }
                request.files.put(inputKeyName, file);
            }
        } else if ("application/x-www-form-urlencoded".equals(contentType)) {
            parseUrlEncodedStrToMap(new String(requestBody));
        }
    }

    @Override
    public HttpRequest getRequest() {
        return request;
    }
}