package org.ear.executor;

import java.util.concurrent.ExecutorService;

class GroupAwareTaskDirectExecutorTest extends AbstractGroupAwareTaskTest {

    @Override
    protected ExecutorService newPool() {
        return new DirectExecutorService();
    }

}
