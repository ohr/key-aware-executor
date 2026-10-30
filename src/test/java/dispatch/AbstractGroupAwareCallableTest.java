package dispatch;

import java.util.concurrent.CompletableFuture;

abstract class AbstractGroupAwareCallableTest extends AbstractGroupAwareExecutorTest {

    @Override
    protected CompletableFuture<?> submit(GroupAwareExecutor executor, Integer group, Runnable body) {
        return executor.submit(() -> {
            body.run();
            return Thread.currentThread().getName();
        }, group);
    }

}
