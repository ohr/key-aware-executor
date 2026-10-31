package org.ear.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

class GroupAwareCallableDefaultExecutorTest extends AbstractGroupAwareCallableTest {

    @Override
    protected ExecutorService newPool() {
        return ForkJoinPool.commonPool();
    }

}
