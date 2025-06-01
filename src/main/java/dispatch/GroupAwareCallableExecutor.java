package dispatch;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static dispatch.CallableWrapper.wrap;
import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * Concrete Implementation of AbstractGroupAwareExecutor for tasks implementing {@link Callable}.
 */
public class GroupAwareCallableExecutor<U>
        extends AbstractGroupAwareExecutor<U, Callable<U>, GroupAwareCallable<U>> {

    public GroupAwareCallableExecutor() {
        super();
    }

    public GroupAwareCallableExecutor(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected CompletableFuture<U> doRunAsync(Callable<U> task, ExecutorService executorService) {
        return supplyAsync(wrap(task), executorService);
    }

    @Override
    protected U doRunSync(Callable<U> task) {
        return wrap(task).get();
    }

    /**
     * Submits a {@link Supplier} that also implements {@link GroupAwareTask}, so that no separate group
     * parameter is necessary. This allows this class to also implement {@link Executor}.
     *
     * @param task Supplier that also implements {@link GroupAwareTask}, e.g. {@link GroupAwareSupplier}.
     * @return task future
     */
    @Override
    public CompletableFuture<U> submit(GroupAwareCallable<U> task) {
        return submit(task, task.getGroup());
    }

}
