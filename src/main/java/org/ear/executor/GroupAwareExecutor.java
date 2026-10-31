package org.ear.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dispatches tasks to a thread pool with the restriction that tasks belonging to the same group
 * are executed sequentially in the order they have been dispatched, while tasks of different
 * groups are executed concurrently.
 * A group can be represented by any non-null object that properly implements {@link Object#hashCode()}
 * and {@link Object#equals(Object)} so that it can be used as the key in a HashMap.
 * <p>
 * Tasks waiting for a predecessor of the same group are only handed over to the underlying
 * {@link ExecutorService} once the predecessor has finished. Therefore, the underlying ExecutorService
 * must not be shut down directly while tasks are pending. Instead, call {@link #shutdown()} and
 * {@link #awaitTermination(long, TimeUnit)} (or {@link #close()}) on this instance first.
 * <p>
 * Optionally, the number of pending tasks, i.e. tasks that have been submitted but not finished yet,
 * can be limited. Once the limit is reached, submitting further tasks blocks until a pending task
 * has finished. Note that a task submitting another task to this executor may then block forever,
 * if all pending tasks are waiting for the submitting task to finish.
 */
public class GroupAwareExecutor implements Executor, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GroupAwareExecutor.class);
    private final ExecutorService executorService;
    private final ConcurrentMap<Object, CompletableFuture<?>> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger pendingTasks = new AtomicInteger();
    private final Semaphore permits;
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private volatile boolean shutdown;

    /**
     * Instantiate instance using the {@link ForkJoinPool#commonPool() common ForkJoinPool}, without
     * limiting the number of pending tasks.
     * <p>
     * <b>Warning:</b> the common pool is shared by the whole JVM (e.g. parallel streams and asynchronous
     * {@link CompletableFuture} stages) and sized for CPU-bound work, i.e. its parallelism is the number
     * of available processors minus one. Tasks that block, e.g. on I/O or locks, occupy its threads and can
     * starve all other users of the pool. For such tasks, use {@link #GroupAwareExecutor(ExecutorService)}
     * with a dedicated pool, e.g. {@link java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()}.
     */
    public GroupAwareExecutor() {
        this(ForkJoinPool.commonPool());
    }

    /**
     * Instantiate instance using a custom {@link ExecutorService}.
     * @param executorService the custom ExecutorService to which the tasks are dispatched
     */
    public GroupAwareExecutor(ExecutorService executorService) {
        this(executorService, Integer.MAX_VALUE);
    }

    /**
     * Instantiate instance using a custom {@link ExecutorService} and limiting the number of pending tasks.
     * @param executorService the custom ExecutorService to which the tasks are dispatched
     * @param maxPendingTasks maximum number of submitted, but not yet finished tasks, beyond which
     *                        submitting a task blocks
     * @throws IllegalArgumentException if maxPendingTasks is not positive
     */
    public GroupAwareExecutor(ExecutorService executorService, int maxPendingTasks) {
        if (maxPendingTasks < 1) {
            throw new IllegalArgumentException("maxPendingTasks must be positive");
        }
        this.executorService = Objects.requireNonNull(executorService);
        this.permits = new Semaphore(maxPendingTasks);
    }

    /**
     * Submits a task that belongs to the given non-null group.
     * <p>
     * The returned future is a copy of the future used internally to sequence the tasks of the group.
     * Cancelling or completing it (e.g. by means of {@link CompletableFuture#orTimeout(long, TimeUnit)})
     * therefore neither stops the task nor affects the execution order of the group.
     * If the task throws a checked exception, the returned future completes exceptionally with a
     * {@link CompletionException} caused by it.
     * <p>
     * If the maximum number of pending tasks is reached, this method blocks until a pending task has finished.
     *
     * @param group group to which the task belongs
     * @param task task to be executed
     * @param <U> result type of the task
     * @return a future representing the status and result of the task
     * @throws NullPointerException if group is null
     * @throws RejectedExecutionException if this executor or the thread pool is shut down, or the
     *                                    calling thread is interrupted while waiting for a pending task to finish
     */
    public <U> CompletableFuture<U> submit(Object group, Callable<U> task) {
        Objects.requireNonNull(task, "Task must not be null");
        Objects.requireNonNull(group, "Group must not be null");
        if (shutdown) {
            throw new RejectedExecutionException("Executor is shut down");
        }
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while waiting for a pending task to finish", e);
        }
        // Count the task as pending before checking for shutdown, so that either shutdown()
        // sees this task as pending or this method sees the shutdown flag.
        pendingTasks.incrementAndGet();
        if (shutdown || executorService.isShutdown()) {
            taskFinished();
            throw new RejectedExecutionException("Executor is shut down");
        }
        // The task is not handed over to the thread pool before the gate is opened after compute()
        // has returned. Otherwise, a thread pool running tasks in the calling thread would execute
        // the task within compute(), and a task submitting to its own group would recursively
        // update the same map entry.
        var gate = new CompletableFuture<Void>();
        // Add the new future entry to the task map or execute after the previous task
        // of the same group has finished.
        log.debug("Submitting task for group {}", group);
        @SuppressWarnings("unchecked") // compute() returns the future created by the remapping function
        var future = (CompletableFuture<U>) tasks.compute(group,
            (g, current) -> current == null ?
                    submitNow(gate, task, group) :
                    submitAfter(gate, current, task, group)
        );
        // Remove the Future from the task map if no task belonging to the same group was
        // dispatched until the current task has finished.
        future.whenComplete((r, e) -> {
            if (tasks.remove(group, future) && log.isDebugEnabled()) {
                log.debug("removed group {}, map size is now {}", group, tasks.size());
            }
            taskFinished();
        });
        gate.complete(null);
        // Never hand out the internal future: completing it early would start the next task
        // of the group while this one is still running.
        return future.copy();
    }

    /**
     * Submits a task without result that belongs to the given non-null group.
     *
     * @param group group to which the task belongs
     * @param task task to be executed
     * @return a future representing the status of the task
     * @throws NullPointerException if group is null
     * @throws RejectedExecutionException if this executor or the thread pool is shut down
     * @see #submit(Object, Callable)
     */
    public CompletableFuture<Void> submit(Object group, Runnable task) {
        Objects.requireNonNull(task, "Task must not be null");
        return submit(group, () -> {
            task.run();
            return null;
        });
    }

    /**
     * Submits a task that knows the group it belongs to, e.g. a {@link GroupAwareRunnable},
     * {@link GroupAwareCallable} or {@link GroupAwareSupplier}.
     * If the task throws a checked exception, the returned future completes exceptionally with a
     * {@link CompletionException} caused by it.
     *
     * @param task task to be executed
     * @param <U> result type of the task
     * @return a future representing the status and result of the task
     * @throws NullPointerException if the group of the task is null
     * @throws RejectedExecutionException if this executor or the thread pool is shut down
     * @see #submit(Object, Callable)
     */
    public <U> CompletableFuture<U> submit(GroupAwareTask<U> task) {
        Callable<U> callable = task::execute;
        return submit(task.getGroup(), callable);
    }

    /**
     * Executes a {@link GroupAwareRunnable}. As there is no future to report a failure to,
     * exceptions thrown by the task are logged.
     *
     * @param command the task, which must implement {@link GroupAwareRunnable}
     * @throws IllegalArgumentException if the task does not implement {@link GroupAwareRunnable}
     * @throws RejectedExecutionException if this executor or the thread pool is shut down
     */
    @Override
    public void execute(Runnable command) {
        if (command instanceof GroupAwareRunnable runnable) {
            submit(runnable).whenComplete((r, e) -> {
                if (e != null) {
                    var cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                    log.error("Task of group {} failed", runnable.getGroup(), cause);
                }
            });
        } else {
            throw new IllegalArgumentException("Argument must implement GroupAwareRunnable");
        }
    }

    private <U> CompletableFuture<U> submitNow(CompletableFuture<Void> gate, Callable<U> task, Object group) {
        log.debug("Immediately submit task of group {}", group);
        return gate.thenApplyAsync(ignored -> call(task), executorService);
    }

    private <U> CompletableFuture<U> submitAfter(CompletableFuture<Void> gate, CompletableFuture<?> future,
                                                 Callable<U> task, Object group) {
        log.debug("Submit task of group {} as soon as the previous task has finished", group);
        // Failures of the previous task must not prevent this task from running
        var previous = future.handle((r, e) -> null);
        return gate.thenCombine(previous, (a, b) -> null)
                .thenApplyAsync(ignored -> call(task), executorService);
    }

    private static <U> U call(Callable<U> task) {
        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private void taskFinished() {
        permits.release();
        if (pendingTasks.decrementAndGet() == 0 && shutdown) {
            terminated.complete(null);
        }
    }

    /**
     * Rejects new tasks, while previously submitted tasks, including those still waiting for
     * their predecessor in the same group, are executed. The underlying ExecutorService is not
     * shut down, as it may be shared.
     */
    public void shutdown() {
        shutdown = true;
        if (pendingTasks.get() == 0) {
            terminated.complete(null);
        }
    }

    /**
     * @return true if {@link #shutdown()} or {@link #close()} has been called
     */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * @return true if this executor has been shut down and all submitted tasks have finished
     */
    public boolean isTerminated() {
        return terminated.isDone();
    }

    /**
     * Blocks until all submitted tasks have finished after a shutdown request, or the timeout occurs.
     * <p>
     * Termination requires a prior call to {@link #shutdown()}. Without it, this method always waits
     * for the full timeout and returns false, even if no tasks are pending.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return true if this executor terminated, false if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            terminated.get(timeout, unit);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException e) {
            throw new IllegalStateException(e); // never completed exceptionally
        }
    }

    /**
     * Shuts down this executor and waits until all submitted tasks have finished.
     * The underlying ExecutorService is not shut down.
     */
    @Override
    public void close() {
        shutdown();
        terminated.join();
    }

    /**
     * @return the number of groups that currently have pending tasks
     */
    public int getGroupMapSize() {
        return tasks.size();
    }

    /**
     * Returns the number of pending tasks, i.e. tasks that have been submitted but not finished yet,
     * across all groups. A task being submitted concurrently may already be counted.
     *
     * @return the number of pending tasks
     */
    public int getPendingTaskCount() {
        return pendingTasks.get();
    }

    /**
     * @param group the group to check
     * @return true if the given group currently has pending tasks
     * @throws NullPointerException if group is null
     */
    public boolean hasPendingTasks(Object group) {
        return tasks.containsKey(Objects.requireNonNull(group, "Group must not be null"));
    }

}
