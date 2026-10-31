package org.ear.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class GroupAwareCallableFixedExecutorTest extends AbstractGroupAwareCallableTest {

    @Override
    protected ExecutorService newPool() {
        return Executors.newFixedThreadPool(5);
    }

}
