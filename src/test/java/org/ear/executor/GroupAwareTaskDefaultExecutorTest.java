package org.ear.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

class GroupAwareTaskDefaultExecutorTest extends AbstractGroupAwareTaskTest {

    @Override
    protected ExecutorService newPool() {
        return ForkJoinPool.commonPool();
    }

}
