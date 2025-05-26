package dispatch;

import java.util.concurrent.Executors;

class GroupAwareRunnableExecutorTest extends AbstractGroupAwareRunnableTest {


    @Override
    protected AbstractGroupAwareExecutor<Void, Runnable, GroupAwareRunnable> newExecutor() {
        return new GroupAwareRunnableExecutor(Executors.newFixedThreadPool(5));
    }

}
