package com.hibegin.common;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class HybridStorage extends BaseLockObject {

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

    public Map<String, Long> getStorageSizeInfoMap() {
        Map<String, Long> storageSizeInfoMap = new HashMap<>();
        storage.entrySet().forEach((e -> {
            storageSizeInfoMap.put(e.getKey(), e.getValue().length());
        }));
        return storageSizeInfoMap.entrySet()
                .stream()
                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue())) // 从大到小排序
                .collect(
                        LinkedHashMap::new, // 结果存放到 LinkedHashMap，保证顺序
                        (m, entry) -> m.put(entry.getKey(), entry.getValue()), // 添加元素
                        Map::putAll // 合并元素
                );
    }

    public long getMemoryThreshold() {
        return memoryThreshold;
    }

    public long getMemoryUsage() {
        return storage.values().stream().mapToLong(e -> {
            if (e.isInMemory()) {
                return e.length();
            }
            return 0;
        }).sum();
    }

    public String put(Storable<?> storable) throws Exception {
        if (getMemoryUsage() + storable.length() > memoryThreshold) {
            return putToDisk(storable);
        }
        return doPut(storable);
    }

    private String doPut(Storable<?> storable) {
        String key = keyGenerator.incrementAndGet() + "";
        lock.lock();
        try {
            storage.put(key, storable);
            return key;
        } catch (Exception e) {
            remove(key);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    public String putToDisk(Storable<?> storable) throws Exception {
        storable.saveToDisk(storageDir);
        if (EnvKit.isDevMode()) {
            File tempFile = storable.getFile();
            String fileInfo = Objects.nonNull(tempFile) ? tempFile.toString() : "unknown";
            LOGGER.warning("HybridStorage put " + storable.getClass().getSimpleName() + " temp file:" + fileInfo + " -> to disk ...");
        }
        return doPut(storable);
    }

    public <T> T get(String key) throws Exception {
        lock.lock();
        try {
            Storable<?> value = storage.get(key);
            if (value == null) {
                return null;
            }
            return (T) value.getData();
        } finally {
            lock.unlock();
        }
    }

    public String getTempFileName(String key) {
        lock.lock();
        try {
            Storable<?> value = storage.get(key);
            if (value == null) {
                return null;
            }
            return value.getTempFileName();
        } finally {
            lock.unlock();
        }
    }

    public InputStream getInputStream(String key) throws Exception {
        lock.lock();
        try {
            Storable<?> value = storage.get(key);
            if (value == null) {
                return null;
            }
            return value.getInputStream();
        } finally {
            lock.unlock();
        }
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
        lock.lock();
        try {
            Storable<?> value = storage.remove(key);
            if (Objects.nonNull(value)) {
                value.clear();
            }
        } finally {
            lock.unlock();
        }

    }

    public <T> T getAndRemove(String key) throws Exception {
        try {
            return get(key);
        } finally {
            remove(key);
        }
    }

    public void clear() {
        storage.keySet().forEach(this::remove);
    }
}