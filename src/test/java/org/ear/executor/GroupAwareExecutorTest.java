package org.ear.executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupAwareExecutorTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final GroupAwareExecutor executor = new GroupAwareExecutor(pool);

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void resultTypesMayDifferPerTask() throws Exception {
        var number = executor.submit("group", () -> 42);
        var text = executor.submit("group", () -> "text");
        assertEquals(42, number.get(10, SECONDS));
        assertEquals("text", text.get(10, SECONDS));
    }

    @Test
    void checkedExceptionOfCallableIsCauseOfFailure() {
        var future = executor.submit("group", () -> {
            throw new IOException("failed");
        });
        var e = assertThrows(ExecutionException.class, () -> future.get(10, SECONDS));
        assertInstanceOf(IOException.class, e.getCause());
    }

    @Test
    void checkedExceptionOfGroupAwareCallableIsCauseOfFailure() {
        var future = executor.submit(new GroupAwareCallable<String>() {
            @Override
            public String call() throws IOException {
                throw new IOException("failed");
            }

            @Override
            public Object getGroup() {
                return "group";
            }
        });
        var e = assertThrows(ExecutionException.class, () -> future.get(10, SECONDS));
        assertInstanceOf(IOException.class, e.getCause());
    }

    @Test
    void executeRunsGroupAwareRunnablesInOrderDespiteFailures() throws InterruptedException {
        List<Integer> executed = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 10; i++) {
            int seq = i;
            executor.execute(GroupAwareRunnable.of("group", () -> {
                executed.add(seq);
                if (seq % 2 == 0) {
                    throw new IllegalStateException("failure of task " + seq);
                }
            }));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, SECONDS));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), executed);
    }

    @Test
    void executeRejectsRunnableWithoutGroup() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(() -> {}));
    }

    @Test
    void submitBlocksWhileLimitIsReached() throws Exception {
        var bounded = new GroupAwareExecutor(pool, 2);
        var release = new CountDownLatch(1);
        bounded.submit("a", () -> await(release));
        bounded.submit("b", () -> await(release));
        var third = CompletableFuture.supplyAsync(() -> bounded.submit("c", () -> "c"));
        Thread.sleep(200);
        assertFalse(third.isDone(), "submit did not block");
        release.countDown();
        assertEquals("c", third.get(10, SECONDS).get(10, SECONDS));
    }

    @Test
    void interruptedSubmitIsRejected() {
        var bounded = new GroupAwareExecutor(pool, 1);
        var release = new CountDownLatch(1);
        bounded.submit("a", () -> await(release));
        Thread.currentThread().interrupt();
        try {
            assertThrows(RejectedExecutionException.class, () -> bounded.submit("b", () -> "b"));
            assertTrue(Thread.interrupted(), "interrupt flag was not restored");
        } finally {
            release.countDown();
        }
    }

    @Test
    void blockedSubmitIsRejectedAfterShutdown() throws Exception {
        var bounded = new GroupAwareExecutor(pool, 1);
        var release = new CountDownLatch(1);
        bounded.submit("a", () -> await(release));
        var blocked = CompletableFuture.runAsync(() -> bounded.submit("b", () -> "b"));
        Thread.sleep(100);
        bounded.shutdown();
        release.countDown();
        var e = assertThrows(ExecutionException.class, () -> blocked.get(10, SECONDS));
        assertInstanceOf(RejectedExecutionException.class, e.getCause());
        assertTrue(bounded.awaitTermination(10, SECONDS));
    }

    @Test
    void factoryMethodsCreateTasksOfGroup() throws Exception {
        var callable = GroupAwareCallable.of("a", () -> "callable");
        var supplier = GroupAwareSupplier.of("b", () -> "supplier");
        var runnable = GroupAwareRunnable.of("c", () -> {});
        assertEquals("a", callable.getGroup());
        assertEquals("b", supplier.getGroup());
        assertEquals("c", runnable.getGroup());
        assertEquals("callable", executor.submit(callable).get(10, SECONDS));
        assertEquals("supplier", executor.submit(supplier).get(10, SECONDS));
        assertNull(executor.submit(runnable).get(10, SECONDS));
    }

    @Test
    void factoryMethodsRejectNullGroup() {
        assertThrows(NullPointerException.class, () -> GroupAwareCallable.of(null, () -> "x"));
        assertThrows(NullPointerException.class, () -> GroupAwareSupplier.of(null, () -> "x"));
        assertThrows(NullPointerException.class, () -> GroupAwareRunnable.of(null, () -> {}));
    }

    @Test
    void reportsPendingTasks() throws Exception {
        var release = new CountDownLatch(1);
        var first = executor.submit("a", () -> await(release));
        var second = executor.submit("a", () -> await(release));
        assertEquals(2, executor.getPendingTaskCount());
        assertTrue(executor.hasPendingTasks("a"));
        assertFalse(executor.hasPendingTasks("b"));
        release.countDown();
        first.get(10, SECONDS);
        second.get(10, SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, SECONDS));
        assertEquals(0, executor.getPendingTaskCount());
        assertFalse(executor.hasPendingTasks("a"));
    }

    @Test
    void reportsShutdownState() throws Exception {
        assertFalse(executor.isShutdown());
        assertFalse(executor.awaitTermination(50, MILLISECONDS), "terminated without shutdown");
        executor.shutdown();
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new GroupAwareExecutor(pool, 0));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
