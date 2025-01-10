package dispatch;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static java.util.concurrent.CompletableFuture.runAsync;

/**
 * Concrete Implementation of AbstractGroupAwareExecutor for tasks implementing {@link Runnable}.
 */
public class GroupAwareRunnableExecutor extends AbstractGroupAwareExecutor<Void, Runnable> implements Executor {

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
     * Submits a Runnable that also implements {@link GroupAware}, so that no separate group
     * parameter is necessary. This allows this class to also implement {@link Executor}.
     *
     * @param task Runnable that also implements {@link GroupAware}, e.g. {@link GroupAwareRunnable}.
     * @return task future
     */
    public CompletableFuture<Void> submit(Runnable task) {
        if (task instanceof GroupAware groupAware) {
            return submit(task, groupAware.getGroup());
        } else {
            throw new IllegalArgumentException("Runnable must implement " + GroupAware.class);
        }
    }

    @Override
    public void execute(Runnable command) {
        submit(command);
    }

}
