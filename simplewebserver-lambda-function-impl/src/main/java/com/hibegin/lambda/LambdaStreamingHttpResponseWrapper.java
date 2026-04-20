package com.hibegin.lambda;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.impl.SimpleHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Lambda 流式响应包装器。
 * <p>
 * 当 Lambda Function URL 配置为 RESPONSE_STREAM 模式时，利用 Lambda Runtime API
 * 的流式响应能力，将 HTTP 响应体以 chunked 方式逐步写回客户端，
 * 而不是像 {@link LambdaHttpResponseWrapper} 一样将所有字节缓冲到内存后一次性返回。
 * </p>
 * <a href="https://docs.aws.amazon.com/lambda/latest/dg/runtimes-custom.html">Lambda Runtime API</a>
 */
public class LambdaStreamingHttpResponseWrapper extends SimpleHttpResponse {

    private static final Logger LOGGER = LoggerUtil.getLogger(LambdaStreamingHttpResponseWrapper.class);

    private final String requestId;
    private final HttpClient httpClient;
    private int statusCode = 200;
    private boolean headerSent;
    private OutputStream runtimeOutputStream;
    private java.net.http.HttpResponse<InputStream> runtimeResponse;
    private Thread senderThread;

    public LambdaStreamingHttpResponseWrapper(HttpRequest request, ResponseConfig responseConfig,
                                              String requestId, HttpClient httpClient) {
        super(request, responseConfig);
        this.requestId = requestId;
        this.httpClient = httpClient;
    }

    @Override
    protected boolean needChunked(InputStream inputStream, long bodyLength) {
        // 始终返回 true 以触发 chunked 写入路径
        return inputStream != null;
    }

    private String getRuntimeApiBaseUrl() {
        return "http://" + System.getenv("AWS_LAMBDA_RUNTIME_API") + "/" + LambdaEventIterator.VERSION + "/runtime/invocation";
    }

    /**
     * 发送字节到 Lambda Runtime API 的流式响应通道。
     * <p>
     * 按照 AWS Lambda Response Streaming 协议：
     * 1. 首次写入时，先发送 JSON prelude（statusCode + headers）
     * 2. 然后发送 8 个 \0 分隔符
     * 3. 之后直接写入 body 数据块
     * </p>
     */
    @Override
    protected void send(byte[] bytes, boolean body, boolean close) {
        try {
            if (!headerSent && (body || close)) {
                sendStreamingPrelude();
                headerSent = true;
            }
            if (body && bytes.length > 0 && runtimeOutputStream != null) {
                runtimeOutputStream.write(bytes);
                runtimeOutputStream.flush();
            }
            if (close && runtimeOutputStream != null) {
                runtimeOutputStream.close();
                awaitRuntimeResponse();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Lambda streaming write error: " + e.getMessage());
            closeQuietly();
        }
    }

    @Override
    protected byte[] wrapperBaseResponseHeader(int statusCode) {
        this.statusCode = statusCode;
        return super.wrapperBaseResponseHeader(statusCode);
    }

    /**
     * 向 Lambda Runtime API 发起流式连接，发送 JSON prelude + \0 分隔符
     */
    private void sendStreamingPrelude() throws IOException {
        // 构建 JSON prelude
        StringBuilder jsonPrelude = new StringBuilder();
        jsonPrelude.append("{\"statusCode\":").append(statusCode);
        jsonPrelude.append(",\"headers\":{");
        Map<String, String> responseHeaders = filterResponseHeaders(getHeader());
        if (responseHeaders != null && !responseHeaders.isEmpty()) {
            String headersJson = responseHeaders.entrySet().stream()
                    .map(e -> "\"" + escapeJson(e.getKey()) + "\":\"" + escapeJson(e.getValue()) + "\"")
                    .collect(Collectors.joining(","));
            jsonPrelude.append(headersJson);
        }
        jsonPrelude.append("}}");

        byte[] preludeBytes = jsonPrelude.toString().getBytes(StandardCharsets.UTF_8);
        byte[] separator = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};

        // 使用 PipedOutputStream/PipedInputStream 实现流式 POST
        java.io.PipedInputStream pipedIn = new java.io.PipedInputStream(64 * 1024);
        java.io.PipedOutputStream pipedOut = new java.io.PipedOutputStream(pipedIn);

        String url = getRuntimeApiBaseUrl() + "/" + requestId + "/response";

        // 在后台线程发起 HTTP 请求到 Lambda Runtime API
        senderThread = new Thread(() -> {
            try {
                java.net.http.HttpRequest runtimeRequest = java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Lambda-Runtime-Function-Response-Mode", "streaming")
                        .header("Content-Type", "application/vnd.awslambda.http-integration-response")
                        .header("Transfer-Encoding", "chunked")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofInputStream(() -> pipedIn))
                        .build();
                runtimeResponse = httpClient.send(runtimeRequest, HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Lambda Runtime API streaming request error: " + e.getMessage());
            }
        }, "lambda-streaming-sender");
        senderThread.start();

        // 保存 outputStream 以供后续 send() 调用使用
        this.runtimeOutputStream = pipedOut;

        // 先写 prelude + 分隔符
        pipedOut.write(preludeBytes);
        pipedOut.write(separator);
        pipedOut.flush();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void closeQuietly() {
        if (runtimeOutputStream != null) {
            try {
                runtimeOutputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static Map<String, String> filterResponseHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }
        Map<String, String> targetHeaders = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String lowerCaseKey = key.toLowerCase();
            if ("content-length".equals(lowerCaseKey) || "connection".equals(lowerCaseKey) || "transfer-encoding".equals(lowerCaseKey)) {
                continue;
            }
            targetHeaders.put(key, entry.getValue());
        }
        return targetHeaders;
    }

    private void awaitRuntimeResponse() {
        if (senderThread == null) {
            return;
        }
        try {
            senderThread.join(3000);
            if (runtimeResponse != null && runtimeResponse.statusCode() >= 400) {
                LOGGER.warning("Lambda Runtime API streaming response error: status = " + runtimeResponse.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Interrupted while waiting runtime response", e);
        }
    }

    /**
     * 标记该响应是否已经通过流式方式发送完毕
     */
    public boolean isStreamingSent() {
        return headerSent;
    }

    /**
     * 获取请求 ID（用于 report 判断是否需要跳过常规上报）
     */
    public String getRequestId() {
        return requestId;
    }
}
