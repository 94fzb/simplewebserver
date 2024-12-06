package com.hibegin.common;

import com.hibegin.common.util.LoggerUtil;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class HybridStorage {

    private static final Logger LOGGER = LoggerUtil.getLogger(HybridStorage.class);
    private final Map<String, Storable<?>> storage = new ConcurrentHashMap<>();
    private final long memoryThreshold; // 内存大小阈值（字节）
    private final String storageDir; // 文件存储目录
    private final AtomicLong keyGenerator = new AtomicLong(0);

    public HybridStorage(long memoryThreshold, String storageDir) {
        this.memoryThreshold = memoryThreshold;
        this.storageDir = storageDir;
        new File(storageDir).mkdirs(); // 确保目录存在
    }

    public String put(Storable<?> storable) throws Exception {
        long totalSize = storage.values().stream().mapToLong(e -> {
            if (e.isInMemory()) {
                return e.length();
            }
            return 0;
        }).sum();
        if (totalSize + storable.length() > memoryThreshold) {
            return putToDisk(storable);
        }
        return doPut(storable);
    }

    private String doPut(Storable<?> storable) {
        String key = keyGenerator.incrementAndGet() + "";
        try {
            storage.put(key, storable);
            return key;
        } catch (Exception e) {
            remove(key);
            throw e;
        }
    }

    public String putToDisk(Storable<?> storable) throws Exception {
        storable.saveToDisk(storageDir);
        File tempFile = storable.getFile();
        String fileName = Objects.nonNull(tempFile) ? tempFile.getName() : "unknown";
        LOGGER.warning("HybridStorage put " + storable.getClass().getSimpleName() + " temp file:" + fileName + " -> to disk ...");
        return doPut(storable);
    }

    public <T> T get(String key) throws Exception {
        Storable<?> value = storage.get(key);
        if (value == null) {
            return null;
        }
        return (T) value.getData();
    }

    public long getLengthByKey(String key) {
        if (Objects.isNull(key)) {
            return 0;
        }
        Storable<?> value = storage.get(key);
        if (value == null) {
            return 0;
        }
        return value.length();
    }

    public void remove(String key) {
        if (Objects.isNull(key)) {
            return;
        }
        Storable<?> value = storage.get(key);
        if (Objects.nonNull(value)) {
            storage.remove(key);
            value.clear();
        }
    }

    public void clear() {
        storage.keySet().forEach(storage::remove);
    }
}