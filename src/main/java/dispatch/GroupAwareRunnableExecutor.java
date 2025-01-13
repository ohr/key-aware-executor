package dispatch;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.CompletableFuture.runAsync;

/**
 * Concrete Implementation of AbstractGroupAwareExecutor for tasks implementing {@link Runnable}.
 */
public class GroupAwareRunnableExecutor
        extends AbstractGroupAwareExecutor<Void, Runnable, GroupAwareRunnable>
        implements Executor {

    public GroupAwareRunnableExecutor() {
        super();
    }

    public GroupAwareRunnableExecutor(ExecutorService executorService) {
        super(executorService);
    }

    @Override
    protected CompletableFuture<Void> doRunAsync(Runnable task, ExecutorService executorService) {
        return runAsync(task, executorService);
    }

    @Override
    protected Void doRunSync(Runnable task) {
        task.run();
        return null;
    }

    /**
     * Submits a Runnable that also implements {@link GroupAwareTask}, so that no separate group
     * parameter is necessary. This allows this class to also implement {@link Executor}.
     *
     * @param task Runnable that also implements {@link GroupAwareTask}, e.g. {@link GroupAwareRunnable}.
     * @return task future
     */
    @Override
    public CompletableFuture<Void> submit(GroupAwareRunnable task) {
        return submit(task, task.getGroup());
    }

    @Override
    public void execute(Runnable command) {
        if (command instanceof GroupAwareRunnable runnable) {
            submit(runnable);
        }
    }
}
