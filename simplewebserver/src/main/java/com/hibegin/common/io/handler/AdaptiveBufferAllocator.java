package com.hibegin.common.io.handler;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class AdaptiveBufferAllocator {

    private static final int[] SIZE_TABLE;
    private static final int increaseThreshold = 3;
    private static final int decreaseThreshold = 3;

    static {
        SIZE_TABLE = new int[32];
        for (int i = 0, size = 16; i < SIZE_TABLE.length; size *= 2, i++) {
            if (size > 0) {
                SIZE_TABLE[i] = size;
            }
        }
    }

    private final int minIndex;
    private final int maxIndex;
    private final int maxSize;
    private int index;
    private int decreaseNow = 0;
    private int increaseNow = 0;
    public AdaptiveBufferAllocator(int minSize, int initialSize, int maxSize) {
        this.minIndex = getSizeTableIndex(minSize);
        this.index = getSizeTableIndex(initialSize);
        this.maxIndex = getSizeTableIndex(maxSize);
        this.maxSize = maxSize;
    }

    public static void main(String[] args) {
        System.out.println("SIZE_TABLE = " + Arrays.toString(SIZE_TABLE));
    }

    public int getMaxSize() {
        return maxSize;
    }

    public ByteBuffer allocateByteBuffer() {
        return ByteBuffer.allocate(currentSize());
    }

    public byte[] allocateByteArray() {
        return new byte[currentSize()];
    }

    public void record(int actualReadBytes) {
        int currentSize = SIZE_TABLE[index];
        if (actualReadBytes >= currentSize) {
            increaseNow++;
            decreaseNow = 0;
            if (increaseNow >= increaseThreshold && index < maxIndex) {
                index++;
                increaseNow = 0;
            }
        } else if (actualReadBytes <= currentSize / 2 && index > minIndex) {
            decreaseNow++;
            increaseNow = 0;
            if (decreaseNow >= decreaseThreshold) {
                index--;
                decreaseNow = 0;
            }
        } else {
            // reset streaks if usage is moderate
            increaseNow = 0;
            decreaseNow = 0;
        }
    }

    public int currentSize() {
        return SIZE_TABLE[index];
    }

    private int getSizeTableIndex(int size) {
        for (int i = 0; i < SIZE_TABLE.length; i++) {
            if (SIZE_TABLE[i] >= size) {
                return i;
            }
        }
        return SIZE_TABLE.length - 1;
    }
}