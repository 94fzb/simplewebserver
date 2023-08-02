package com.hibegin.common;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BaseLockObject implements LockObject {
    protected final Lock lock = new ReentrantLock();
    protected final Condition condition = lock.newCondition();

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public Condition getCondition() {
        return condition;
    }
}
