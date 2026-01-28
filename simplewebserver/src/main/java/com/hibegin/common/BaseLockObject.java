package com.hibegin.common;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BaseLockObject implements LockObject {
    protected final ReentrantLock lock;
    protected final Condition condition;

    public BaseLockObject() {
        this(false);
    }

    public BaseLockObject(boolean fair) {
        this.lock = new ReentrantLock(fair);
        this.condition = lock.newCondition();
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public Condition getCondition() {
        return condition;
    }
}
