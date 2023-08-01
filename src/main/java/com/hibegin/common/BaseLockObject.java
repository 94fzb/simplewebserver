package com.hibegin.common;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BaseLockObject implements LockObject {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public Condition getCondition() {
        return condition;
    }
}
