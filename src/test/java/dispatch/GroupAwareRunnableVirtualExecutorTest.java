package dispatch;

import java.util.concurrent.Executors;

class GroupAwareRunnableVirtualExecutorTest extends AbstractGroupAwareRunnableTest {


    @Override
    protected AbstractGroupAwareExecutor<Void, Runnable, GroupAwareRunnable> newExecutor() {
        return new GroupAwareRunnableExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

}
