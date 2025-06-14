package com.hibegin.common;

public class SleepUtils {
    /**
     * 无锁模式
     *
     * @param lockObject
     * @param sleepInMs
     * @return
     * @throws InterruptedException
     */
    public static int noCpuCompeteSleep(LockObject lockObject, int sleepInMs) throws InterruptedException {
        lockObject.getLock().lock();
        try {
            long nanos = sleepInMs * 1000000L; // 转换为纳秒
            while (nanos > 0) {
                nanos = lockObject.getCondition().awaitNanos(nanos);
            }
            return sleepInMs;
        } finally {
            lockObject.getLock().unlock();
        }
    }

}
