package dispatch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class GroupAwareRunnableVirtualExecutorTest extends AbstractGroupAwareRunnableTest {

    @Override
    protected ExecutorService newPool() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

}
