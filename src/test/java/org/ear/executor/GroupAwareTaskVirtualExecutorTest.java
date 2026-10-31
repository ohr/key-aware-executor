package org.ear.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class GroupAwareTaskVirtualExecutorTest extends AbstractGroupAwareTaskTest {

    @Override
    protected ExecutorService newPool() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

}
