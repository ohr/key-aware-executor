package dispatch;

import java.util.concurrent.ExecutorService;

class GroupAwareCallableDirectExecutorTest extends AbstractGroupAwareCallableTest {

    @Override
    protected ExecutorService newPool() {
        return new DirectExecutorService();
    }

}
