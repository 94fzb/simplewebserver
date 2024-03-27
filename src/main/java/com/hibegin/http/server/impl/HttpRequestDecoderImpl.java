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
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.util.FileCacheKit;
import com.hibegin.http.server.util.HttpQueryStringUtils;

import java.io.*;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpRequestDecoderImpl implements HttpRequestDeCoder {

    private static final String CRLF = "\r\n";
    static final String SPLIT = CRLF + CRLF;
    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestDecoderImpl.class);
    private SimpleHttpRequest request;
    private byte[] inputBytes = new byte[]{};
    private final RequestConfig requestConfig;
    private final ApplicationContext applicationContext;
    private final ReadWriteSelectorHandler handler;

    public HttpRequestDecoderImpl(RequestConfig requestConfig, ApplicationContext applicationContext, ReadWriteSelectorHandler handler) {
        this.requestConfig = requestConfig;
        this.applicationContext = applicationContext;
        this.handler = handler;
    }

    @Override
    public ReadWriteSelectorHandler getHandler() {
        return handler;
    }

    private static int findSequence(byte[] data, byte[] sequence) {
        if (data == null || sequence == null || data.length < sequence.length) {
            return -1; // 数据为空或序列长度不足
        }

        for (int i = 0; i < data.length - sequence.length + 1; i++) {
            boolean match = true;
            for (int j = 0; j < sequence.length; j++) {
                if (data[i + j] != sequence[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i; // 找到序列的起始索引
            }
        }

        return -1; // 未找到序列
    }

    @Override
    public Map.Entry<Boolean, ByteBuffer> doDecode(ByteBuffer byteBuffer) throws Exception {
        if (Objects.isNull(request)) {
            this.request = new SimpleHttpRequest(handler, applicationContext, requestConfig);
        }
        Map.Entry<Boolean, ByteBuffer> result;
        //代理头，直接保存到body
        if (request.method == HttpMethod.CONNECT) {
            result = saveRequestBodyBytes(byteBuffer.array());
        }
        //存在body需要处理
        else if (inputBytes.length > 0 && getContentLength() > 0) {
            result = saveRequestBodyBytes(byteBuffer.array());
        } else {
            // 存在2种情况,提交的数据一次性读取完成,提交的数据一次性读取不完
            inputBytes = BytesUtil.mergeBytes(inputBytes, byteBuffer.array());
            int idx = findSequence(inputBytes, SPLIT.getBytes());
            if (idx < 0) {
                int maxHeaderSize = request.getRequestConfig().getMaxRequestHeaderSize();
                //没有读取到 SPLIT 时，检查 header 的最大长度
                if (inputBytes.length > maxHeaderSize) {
                    throw new RequestBodyTooLargeException("The http header to large " + inputBytes.length + " more than " + maxHeaderSize);
                }
                //没有 SPLIT，请求头部分不完整，需要继续等待，且已处理 byteBuffer，返回0
                return new AbstractMap.SimpleEntry<>(false, ByteBuffer.allocate(0));
            }
            String httpHeader = new String(BytesUtil.subBytes(inputBytes, 0, idx));
            String[] headerArr = httpHeader.split(CRLF);
            //parse http method
            request.method = HttpMethod.parseHttpMethodByRequestLine(headerArr[0]);
            // parse HttpHeader
            parseProtocolHeader(headerArr);
            request.requestHeaderStr = httpHeader;
            // parse body
            int headerByteLength = SPLIT.getBytes().length + idx;
            byte[] requestBody = BytesUtil.subBytes(inputBytes, headerByteLength, inputBytes.length - headerByteLength);
            result = saveRequestBodyBytes(requestBody);
        }
        if (Objects.equals(result.getKey(), true)) {
            dealRequestBodyData();
            //处理完成，清空byte[]
            inputBytes = new byte[]{};
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
        long dataLength = getContentLength();
        if (Objects.isNull(bytes) || bytes.length == 0) {
            if (dataLength == 0) {
                return new AbstractMap.SimpleEntry<>(true, ByteBuffer.allocate(0));
            }
            return new AbstractMap.SimpleEntry<>(dataLength == getRequestBodyLength(), ByteBuffer.allocate(0));
        }
        byte[] handleBytes = bytes;
        try {
            if (dataLength > 0) {
                handleBytes = BytesUtil.subBytes(bytes, 0, (int) dataLength);
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
        if (Objects.isNull(request.tmpRequestBodyFile)) {
            request.tmpRequestBodyFile = FileCacheKit.generatorRequestTempFile(request.getServerConfig().getPort() + "", handleBytes);
            return request.tmpRequestBodyFile;
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(request.tmpRequestBodyFile, true)) {
            fileOutputStream.write(handleBytes);
        }
        return request.tmpRequestBodyFile;
    }

    private byte[] getRequestBodyBytes() {
        if (request.tmpRequestBodyFile != null && request.tmpRequestBodyFile.exists()) {
            return IOUtil.getByteByFile(request.tmpRequestBodyFile);
        } else {
            return null;
        }
    }

    private long getRequestBodyLength() {
        if (request.tmpRequestBodyFile != null && request.tmpRequestBodyFile.exists()) {
            return request.tmpRequestBodyFile.length();
        }
        return 0;
    }

    private void parseProtocolHeader(String[] headerArr) throws Exception {
        //
        request.header.clear();
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
            request.queryStr = "";
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
        request.paramMap = HttpQueryStringUtils.parseUrlEncodedStrToMap(request.queryStr);
        if (getContentLength() > getRequest().getRequestConfig().getMaxRequestBodySize()) {
            throw new RequestBodyTooLargeException("The Content-Length outside the max upload size " + ConfigKit.getMaxRequestBodySize());
        }
    }

    private long getContentLength() {
        return Long.parseLong(Objects.requireNonNullElse(request.getHeader("Content-Length"), "0"));
    }

    private void dealRequestHeaderString(String str) {
        if (str.contains(":")) {
            request.header.put(str.split(":")[0], str.substring(str.indexOf(":") + 1).trim());
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
            request.paramMap = HttpQueryStringUtils.parseUrlEncodedStrToMap(new String(requestBody));
        }
    }

    @Override
    public HttpRequest getRequest() {
        return request;
    }

    @Override
    public void doNext() {
        //处理完成，清空byte[]
        this.inputBytes = new byte[]{};
        this.request = null;
    }
}