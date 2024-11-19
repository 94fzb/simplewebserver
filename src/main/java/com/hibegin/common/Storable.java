package com.hibegin.common;

import java.io.File;
import java.io.IOException;

public interface Storable<T> {
    /**
     * 获取数据的长度。
     *
     * @return 数据的长度
     */
    long length();

    File getFile();

    /**
     * 读取原始数据。
     *
     * @return 原始数据
     * @throws IOException 如果读取过程中发生 I/O 错误
     */
    T getData() throws Exception;

    /**
     * 判断数据是否存储在内存中。
     *
     * @return 如果数据在内存中，返回 true；否则返回 false
     */
    boolean isInMemory();

    /**
     * 将数据从内存保存到磁盘。
     *
     * @param filePath 保存文件的路径
     * @throws IOException 如果保存过程中发生 I/O 错误
     */
    void saveToDisk(String filePath) throws Exception;


    void clear();

}
