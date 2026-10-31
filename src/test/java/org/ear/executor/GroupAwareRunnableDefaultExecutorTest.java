package org.ear.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

class GroupAwareRunnableDefaultExecutorTest extends AbstractGroupAwareRunnableTest {

    @Override
    protected ExecutorService newPool() {
        return ForkJoinPool.commonPool();
    }

}
