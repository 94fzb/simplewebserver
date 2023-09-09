package com.hibegin.common;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public interface LockObject {

    Lock getLock();

    Condition getCondition();
}
