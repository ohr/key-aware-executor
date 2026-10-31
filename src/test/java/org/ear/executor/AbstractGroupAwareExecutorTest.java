package org.ear.executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Lost permits of the pending task limit would let the tests hang
@Timeout(60)
abstract class AbstractGroupAwareExecutorTest {

    private static final int GROUPS = 100;
    private static final int MAX_WAIT = 10;
    private static final int LOOPS = 1000;
    private static final int PRODUCERS = 4;
    // Small enough that producers regularly block
    private static final int MAX_PENDING_TASKS = 16;

    // next sequence number per group, assigned at submission
    private final Map<Integer, AtomicInteger> submitted = new ConcurrentHashMap<>();
    // sequence numbers per group in the order the tasks started
    private final Map<Integer, List<Integer>> executed = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicBoolean> running = new ConcurrentHashMap<>();
    private final AtomicInteger executions = new AtomicInteger();
    private final AtomicInteger overlaps = new AtomicInteger();

    private ExecutorService pool;
    private GroupAwareExecutor executor;

    protected abstract ExecutorService newPool();

    /**
     * Submits a task of the given group that runs the body, using one of the submission methods
     */
    protected abstract CompletableFuture<?> submit(GroupAwareExecutor executor, Integer group, Runnable body);

    @BeforeEach
    void setUp() {
        pool = newPool();
        executor = new GroupAwareExecutor(pool, MAX_PENDING_TASKS);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void tasksOfSameGroupRunInSubmissionOrder() throws InterruptedException {
        var random = ThreadLocalRandom.current();
        for (int i = 0; i < LOOPS; i++) {
            submit(random.nextInt(GROUPS), random.nextInt(MAX_WAIT), i % 5 == 0);
        }
        shutdownAndVerify(LOOPS);
    }

    @Test
    void concurrentProducers() throws InterruptedException {
        // Each producer owns a disjoint set of groups, so that the submission order per group is well-defined
        try (var producers = Executors.newFixedThreadPool(PRODUCERS)) {
            for (int p = 0; p < PRODUCERS; p++) {
                int producer = p;
                producers.execute(() -> {
                    var random = ThreadLocalRandom.current();
                    for (int i = 0; i < LOOPS / PRODUCERS; i++) {
                        submit(random.nextInt(GROUPS / PRODUCERS) * PRODUCERS + producer,
                                random.nextInt(MAX_WAIT), i % 5 == 0);
                    }
                });
            }
        }
        shutdownAndVerify(LOOPS);
    }

    @Test
    void completingReturnedFutureDoesNotBreakOrdering() throws InterruptedException {
        submit(1, 200, false).orTimeout(10, MILLISECONDS);
        submit(1, 200, false).cancel(true);
        submit(1, 200, false).complete(null);
        submit(1, 10, false);
        shutdownAndVerify(4);
    }

    @Test
    void pendingTasksAreExecutedAfterShutdown() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            submit(1, 5, false);
        }
        // Most tasks are still waiting for their predecessor when shutting down
        shutdownAndVerify(20);
    }

    @Test
    void tasksCanSubmitToTheirOwnGroup() throws InterruptedException {
        var resubmitted = new CountDownLatch(1);
        int seq = submitted.computeIfAbsent(1, g -> new AtomicInteger()).getAndIncrement();
        submit(executor, 1, () -> {
            execute(1, seq, 0, false);
            submit(1, 0, false);
            resubmitted.countDown();
        });
        // Shutting down earlier would reject the nested submission
        assertTrue(resubmitted.await(10, SECONDS), "nested task was not submitted");
        shutdownAndVerify(2);
    }

    @Test
    void rejectsTasksAfterShutdown() {
        executor.shutdown();
        assertTrue(executor.isTerminated());
        assertThrows(RejectedExecutionException.class, () -> submit(1, 0, false));
    }

    @Test
    void rejectsNullGroup() {
        assertThrows(NullPointerException.class, () -> submit(executor, null, () -> {}));
    }

    private CompletableFuture<?> submit(int group, long sleepMillis, boolean fail) {
        int seq = submitted.computeIfAbsent(group, g -> new AtomicInteger()).getAndIncrement();
        return submit(executor, group, () -> execute(group, seq, sleepMillis, fail));
    }

    private void execute(int group, int seq, long sleepMillis, boolean fail) {
        var flag = running.computeIfAbsent(group, g -> new AtomicBoolean());
        if (!flag.compareAndSet(false, true)) {
            overlaps.incrementAndGet();
        }
        try {
            executed.computeIfAbsent(group, g -> Collections.synchronizedList(new ArrayList<>())).add(seq);
            executions.incrementAndGet();
            Thread.sleep(sleepMillis);
            if (fail) {
                throw new RuntimeException("failure of task " + seq + " in group " + group);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            flag.set(false);
        }
    }

    private void shutdownAndVerify(int expectedExecutions) throws InterruptedException {
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, SECONDS), "tasks did not finish in time");
        assertEquals(expectedExecutions, executions.get());
        assertEquals(0, overlaps.get(), "tasks of the same group overlapped");
        assertEquals(0, executor.getGroupMapSize(), "group map is not clean");
        submitted.forEach((group, count) ->
                assertEquals(IntStream.range(0, count.get()).boxed().toList(), executed.get(group),
                        "execution order of group " + group));
    }

}
