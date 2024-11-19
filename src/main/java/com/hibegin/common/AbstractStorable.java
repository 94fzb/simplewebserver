package com.hibegin.common;

import com.hibegin.common.util.IOUtil;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public abstract class AbstractStorable<T> implements Storable<T> {
    protected T data;
    protected String tempFileName;
    protected File file;

    public AbstractStorable(T data, String tempFileName) {
        this.data = data;
        this.tempFileName = tempFileName;
    }

    @Override
    public long length() {
        if (data != null) {
            return getDataSize(data);
        } else if (file != null && file.exists()) {
            return file.length();
        }
        return 0;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public T getData() throws Exception {
        if (data != null) {
            return data;
        } else if (file != null && file.exists()) {
            data = deserialize(IOUtil.getByteByFile(file));
            return data;
        }
        return null;
    }

    @Override
    public boolean isInMemory() {
        return data != null;
    }

    @Override
    public void saveToDisk(String filePath) throws Exception {
        if (data != null) {
            file = serializeToFile(data, filePath);
            data = null;
        }
    }

    @Override
    public void clear() {
        this.data = null;
        if (Objects.nonNull(file)) {
            this.file.delete();
        }
        this.file = null;
    }

    /**
     * 获取数据的大小。
     *
     * @param data 数据
     * @return 数据的大小
     */
    protected abstract long getDataSize(T data);

    /**
     * 序列化数据。
     *
     * @throws IOException 如果序列化过程中发生 I/O 错误
     */
    protected abstract File serializeToFile(T data, String filePath) throws Exception;

    /**
     * 反序列化数据。
     *
     * @param bytes 输入流
     * @return 数据
     * @throws IOException 如果反序列化过程中发生 I/O 错误
     */
    protected abstract T deserialize(byte[] bytes) throws Exception;
}