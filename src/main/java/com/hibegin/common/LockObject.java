package com.hibegin.common;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

public interface LockObject {

    Lock getLock();

    Condition getCondition();
}
