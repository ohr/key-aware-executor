package org.ear.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class GroupAwareRunnableFixedExecutorTest extends AbstractGroupAwareRunnableTest {

    @Override
    protected ExecutorService newPool() {
        return Executors.newFixedThreadPool(5);
    }

}
