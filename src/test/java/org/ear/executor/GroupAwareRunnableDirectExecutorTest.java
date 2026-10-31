package org.ear.executor;

import java.util.concurrent.ExecutorService;

class GroupAwareRunnableDirectExecutorTest extends AbstractGroupAwareRunnableTest {

    @Override
    protected ExecutorService newPool() {
        return new DirectExecutorService();
    }

}
