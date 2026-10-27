package com.hibegin.http.server.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * A received file and its multipart metadata. The request owns the temporary file
 * and removes it when request processing finishes. Copy its contents before then
 * if they are needed outside this request.
 */
public final class UploadedFile {

    private final String fieldName;
    private final String originalFilename;
    private final String contentType;
    private final long size;
    private final File file;

    public UploadedFile(String fieldName, String originalFilename, String contentType, File file) {
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName");
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.file = Objects.requireNonNull(file, "file");
        this.size = file.length();
    }

    /** Returns the multipart form field name, such as {@code file}. */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Returns the client-supplied filename, empty if no file was selected, or
     * {@code null} when unavailable. It may contain path information and must not
     * be used directly as a destination path.
     */
    public String getOriginalFilename() {
        return originalFilename;
    }

    /** Returns the client-declared content type, or {@code null} when absent. */
    public String getContentType() {
        return contentType;
    }

    /** Returns the actual received file size in bytes, captured when parsed. */
    public long getSize() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Returns the same temporary file exposed by {@link HttpRequest#getFile(String)}. */
    public File getFile() {
        return file;
    }

    /**
     * Opens a new, independent file stream positioned at the beginning. The caller
     * must close it before request cleanup. Fails if the file was moved or deleted.
     */
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }
}
