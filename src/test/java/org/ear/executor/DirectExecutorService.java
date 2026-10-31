package org.ear.executor;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * ExecutorService that runs tasks in the calling thread
 */
class DirectExecutorService extends AbstractExecutorService {

    private volatile boolean shutdown;

    @Override
    public void execute(@NonNull Runnable command) {
        if (shutdown) {
            throw new RejectedExecutionException("Executor is shut down");
        }
        command.run();
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) {
        return shutdown;
    }
}
