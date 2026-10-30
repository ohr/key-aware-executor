package dispatch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class GroupAwareCallableVirtualExecutorTest extends AbstractGroupAwareCallableTest {

    @Override
    protected ExecutorService newPool() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

}
