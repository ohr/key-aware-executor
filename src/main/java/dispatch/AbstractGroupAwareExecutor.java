package dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

import static java.util.Objects.requireNonNull;

/**
 * Base class for dispatching tasks to a thread pool with the restriction that tasks belonging
 * to the same group are executed in the order they have been dispatched.
 * A group can be represented by any non-null object that properly implements {@link Object#hashCode()}
 * so that it can be used as key in a HashMap.
 *
 * @param <U> result of the task
 * @param <T> type of the task
 */
public abstract class AbstractGroupAwareExecutor<U, T, G extends GroupAwareTask<U>> {

    private static final Logger log = LoggerFactory.getLogger(AbstractGroupAwareExecutor.class);
    private final ExecutorService executorService;
    private final ConcurrentMap<Object, CompletableFuture<U>> tasks = new ConcurrentHashMap<>();

    /**
     * Instantiate instance using the default {@link ForkJoinPool}.
     */
    public AbstractGroupAwareExecutor() {
        this(ForkJoinPool.commonPool());
    }

    /**
     * Instantiate instance using a custom {@link ExecutorService}.
     * @param executorService custom ExecutorService to which the tasks are dispatched
     */
    public AbstractGroupAwareExecutor(ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Submits a task that belongs to the given non-null group
     *
     * @param task task to be executed
     * @param group group to which the task belongs
     * @return a future representing the status and result of the task
     * @throws NullPointerException if group is null
     * @throws RejectedExecutionException if thread pool is shut down
     */
    public CompletableFuture<U> submit(T task, Object group) {
        if (executorService.isShutdown()) {
            throw new RejectedExecutionException("Executor is shut down");
        }
        // Add new future entry to task map or execute after previous task with
        // same group has finished.
        log.debug("Submitting task for group {}", group);
        var future = tasks.compute(
            requireNonNull(group, "group must not be null"),
            (g, current) -> current == null ?
                    submitNow(task, group) :
                    submitAfter(current, task, group)
        );
        // Remove future from task map if group was not used again while executing
        future.whenComplete((r, e) -> {
            if (tasks.remove(group, future) && log.isDebugEnabled()) {
                log.debug("removed group {}, map size is now {}", group, tasks.size());
            }
        });
        return future;
    }

    public abstract CompletableFuture<U> submit(G task);

    private CompletableFuture<U> submitNow(T task, Object group) {
        log.debug("Immediately submit task for group {}", group);
        return doRunAsync(task, executorService);
    }

    private CompletableFuture<U> submitAfter(CompletableFuture<U> future, T task, Object group) {
        log.debug("Submit task for group {} when previous tasks have finished", group);
        return future.handleAsync((r, e) -> doRunSync(task), executorService);
    }

    /**
     * Run task asynchronously using the executorService
     *
     * @param task task
     * @param executorService thread pool
     * @return future of the task
     */
    protected abstract CompletableFuture<U> doRunAsync(T task, ExecutorService executorService);

    /**
     * Run task synchronously
     *
     * @param task task
     * @return result of the task
     */
    protected abstract U doRunSync(T task);

    public int getGroupMapSize() {
        return tasks.size();
    }

}
