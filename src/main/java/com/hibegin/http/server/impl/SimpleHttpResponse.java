package com.hibegin.http.server.impl;

import com.hibegin.common.util.BytesUtil;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.io.ChunkedOutputStream;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.*;
import com.hibegin.http.server.web.cookie.Cookie;
import com.hibegin.http.server.execption.InternalException;
import com.hibegin.http.server.handler.ReadWriteSelectorHandler;
import com.hibegin.http.server.io.GzipCompressingInputStream;
import flexjson.JSONSerializer;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class SimpleHttpResponse implements HttpResponse {

    private static final String CRLF = "\r\n";
    private static Logger LOGGER = LoggerUtil.getLogger(SimpleHttpResponse.class);
    private Map<String, String> header = new HashMap<String, String>();
    private HttpRequest request;
    private List<Cookie> cookieList = new ArrayList<Cookie>();
    private ReadWriteSelectorHandler handler;
    private ResponseConfig responseConfig;
    private boolean keepAlive;

    public SimpleHttpResponse(HttpRequest request, ResponseConfig responseConfig) {
        header.put("Server", ServerInfo.getName() + "/" + ServerInfo.getVersion());
        this.handler = request.getHandler();
        this.request = request;
        this.responseConfig = responseConfig;
        if (responseConfig.isGzip()) {
            header.put("Content-Encoding", "gzip");
        }

        keepAlive = request.getHeader("Connection") != null && "keep-alive".equalsIgnoreCase(request.getHeader("Connection"));
        if (keepAlive) {
            getHeader().put("Connection", "keep-alive");
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.isCreate()) {
                    cookieList.add(cookie);
                }
            }
        }
    }

    @Override
    public void writeFile(File file) {
        if (file.exists()) {
            try {
                if (file.isDirectory()) {
                    renderByStatusCode(302);
                    return;
                }

                String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1);
                // getMimeType
                if (header.get("Content-Type") == null) {
                    header.put("Content-Type", MimeTypeUtil.getMimeStrByExt(ext));
                }

                ByteArrayOutputStream fout = new ByteArrayOutputStream();
                if (file.length() < 1024 * 1024) {
                    fout.write(wrapperData(200, IOUtil.getByteByInputStream(new FileInputStream(file))));
                    send(fout);
                } else {
                    fout.write(wrapperResponseHeader(200, file.length()));
                    send(fout, false);
                    //处理大文件
                    FileInputStream fileInputStream = new FileInputStream(file);
                    int length;
                    byte tempByte[] = new byte[512 * 1204];
                    while ((length = fileInputStream.read(tempByte)) != -1) {
                        ByteArrayOutputStream bout = new ByteArrayOutputStream();
                        bout.write(tempByte, 0, length);
                        send(bout, false);
                    }
                    fileInputStream.close();
                    handler.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            renderByStatusCode(404);
        }
    }

    public void send(ByteArrayOutputStream outputStream, boolean close) {
        try {
            byte[] b = outputStream.toByteArray();
            ByteBuffer byteBuffer = ByteBuffer.allocate(b.length);
            byteBuffer.put(b);
            handler.handleWrite(byteBuffer);
            outputStream.write(b);
            if (close) {
                handler.close();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "send error", e);
            throw new InternalException("send error", e);
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "outputStream close exception ", e);
            }
        }
    }


    private void send(ByteArrayOutputStream fout) {
        send(fout, true);
    }

    @Override
    public void renderJson(Object json) {
        try {
            renderByMimeType("json", new JSONSerializer().deepSerialize(json).getBytes(responseConfig.getCharSet()));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return
     * @throws IOException
     */
    private byte[] wrapperData(Integer statusCode, byte[] data) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bout.write(wrapperResponseHeader(statusCode, data.length));
        if (data.length > 0) {
            bout.write(convertGzipBytes(data));
        }
        return bout.toByteArray();
    }

    private byte[] wrapperResponseHeader(Integer statusCode, long length) {
        header.put("Content-Length", length + "");
        return wrapperBaseResponseHeader(statusCode);
    }

    private byte[] wrapperBaseResponseHeader(int statusCode) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(StatusCodeUtil.getStatusCode(statusCode)).append(CRLF);
        for (Entry<String, String> he : header.entrySet()) {
            sb.append(he.getKey()).append(": ").append(he.getValue()).append(CRLF);
        }
        //deal cookie
        if (!responseConfig.isDisableCookie()) {
            for (Cookie cookie : cookieList) {
                sb.append("Set-Cookie: ").append(cookie).append(CRLF);
            }
        }
        sb.append(CRLF);
        return sb.toString().getBytes();
    }

    private byte[] wrapperResponseHeader(Integer statusCode) {
        header.put("Transfer-Encoding", "chunked");
        return wrapperBaseResponseHeader(statusCode);
    }

    private byte[] convertGzipBytes(byte[] bytes) {
        if (responseConfig.isGzip()) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length);
                GZIPOutputStream gzip = new GZIPOutputStream(bos);
                gzip.write(bytes);
                gzip.close();
                byte[] compressed = bos.toByteArray();
                bos.close();
                return compressed;
            } catch (IOException e) {
                throw new InternalException("convertGzipBytes error ", e);
            }
        }
        return bytes;
    }

    private void renderByStatusCode(Integer errorCode) {
        if (errorCode > 399) {
            ByteArrayOutputStream fout = new ByteArrayOutputStream();
            try {
                header.put("Content-Type", "text/html");
                fout.write(wrapperData(errorCode, StringsUtil.getHtmlStrByStatusCode(errorCode).getBytes()));
                send(fout);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } else if (errorCode >= 300 && errorCode < 400) {
            ByteArrayOutputStream fout = new ByteArrayOutputStream();
            try {
                if (!header.containsKey("Location")) {
                    header.put("Location", "http://" + request.getHeader("Host") + "/" + request.getUri() + "index.html");
                }
                fout.write(wrapperData(errorCode, new byte[]{}));
                send(fout);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public void renderCode(int code) {
        renderByStatusCode(code);
    }

    @Override
    public void renderHtml(String urlPath) {
        writeFile(new File(PathUtil.getStaticPath() + urlPath));
    }

    @Override
    public void addCookie(Cookie cookie) {
        cookieList.add(cookie);
    }

    @Override
    public void renderHtmlStr(String htmlContent) {
        try {
            renderByMimeType("html", htmlContent.getBytes(responseConfig.getCharSet()));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void renderByMimeType(String ext, byte[] body) {
        ByteArrayOutputStream fout = new ByteArrayOutputStream();
        header.put("Content-Type", MimeTypeUtil.getMimeStrByExt(ext) + ";charset=" + responseConfig.getCharSet());
        try {
            fout.write(wrapperData(200, body));
            send(fout);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] warpperToChunkedByte(byte[] data) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ChunkedOutputStream out = new ChunkedOutputStream(bout);
        try {
            int block = 128;
            int blockCount = data.length / block;
            for (int i = 0; i < blockCount; i++) {
                out.write(BytesUtil.subBytes(data, i * block, block));
            }
            int last = data.length % block;
            if (last != 0) {
                out.write(BytesUtil.subBytes(data, blockCount * block, last));
            }
            out.close();
            return bout.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    @Override
    public void addHeader(String name, String value) {
        header.put(name, value);
    }

    @Override
    public void redirect(String url) {
        ByteArrayOutputStream fout = new ByteArrayOutputStream();
        header.put("Location", url);
        try {
            fout.write(wrapperData(302, new byte[0]));
            send(fout);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void forward(String url) {
        redirect(request.getScheme() + "://" + request.getHeader("Host") + "/" + url);
    }

    @Override
    public void renderFile(File file) {
        if (file.exists()) {
            header.put("Content-Disposition", "attachment;filename=" + file.getName());
        }
        writeFile(file);
    }

    @Override
    public void renderFreeMarker(String name) {
        renderHtmlStr(FreeMarkerUtil.renderToFM(name, request));
    }

    @Override
    public void write(InputStream inputStream) {
        write(inputStream, 200);
    }

    @Override
    public void write(InputStream inputStream, int code) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(wrapperResponseHeader(code));
            send(byteArrayOutputStream, false);
            if (inputStream != null) {
                byte[] bytes = new byte[4096];
                int length;
                if (responseConfig.isGzip()) {
                    inputStream = new GzipCompressingInputStream(inputStream);
                }
                while ((length = inputStream.read(bytes)) != -1) {
                    ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
                    ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(tmpOut);
                    chunkedOutputStream.write(BytesUtil.subBytes(bytes, 0, length));
                    send(tmpOut, false);
                }
                ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
                ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(tmpOut);
                chunkedOutputStream.close();
                send(tmpOut);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public Map<String, String> getHeader() {
        return header;
    }

    @Override
    public void renderText(String text) {
        try {
            renderByMimeType("text", text.getBytes(responseConfig.getCharSet()));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
