package dispatch;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * Concrete Implementation of AbstractGroupAwareExecutor for tasks implementing {@link Supplier}.
 */
public class GroupAwareSupplierExecutor<U>
        extends AbstractGroupAwareExecutor<U, Supplier<U>, GroupAwareSupplier<U>> {

    public GroupAwareSupplierExecutor() {
        super();
    }

    public GroupAwareSupplierExecutor(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected CompletableFuture<U> doRunAsync(Supplier<U> task, ExecutorService executorService) {
        return supplyAsync(task, executorService);
    }

    @Override
    protected U doRunSync(Supplier<U> task) {
        return task.get();
    }

    public CompletableFuture<U> submit(Object key, Function<Object, U> consumer) {
        return submit(() -> consumer.apply(key), key);
    }

    /**
     * Submits a Supplier that also implements {@link GroupAwareTask}, so that no separate group
     * parameter is necessary. This allows this class to also implement {@link Executor}.
     *
     * @param task Supplier that also implements {@link GroupAwareTask}, e.g. {@link GroupAwareSupplier}.
     * @return task future
     */
    @Override
    public CompletableFuture<U> submit(GroupAwareSupplier<U> task) {
        return submit(task, task.getGroup());
    }

}
