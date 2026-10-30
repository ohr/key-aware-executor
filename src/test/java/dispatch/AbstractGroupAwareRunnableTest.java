package dispatch;

import java.util.concurrent.CompletableFuture;

abstract class AbstractGroupAwareRunnableTest extends AbstractGroupAwareExecutorTest {

    @Override
    protected CompletableFuture<?> submit(GroupAwareExecutor executor, Integer group, Runnable body) {
        return executor.submit(body, group);
    }

}
