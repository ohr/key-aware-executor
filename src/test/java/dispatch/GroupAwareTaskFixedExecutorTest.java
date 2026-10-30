package dispatch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class GroupAwareTaskFixedExecutorTest extends AbstractGroupAwareTaskTest {

    @Override
    protected ExecutorService newPool() {
        return Executors.newFixedThreadPool(5);
    }

}
