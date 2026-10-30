# key-aware-executor

`GroupAwareExecutor` dispatches tasks to an `ExecutorService` while guaranteeing that tasks
belonging to the same *group* (key) run **sequentially, in submission order**. Tasks of different
groups run concurrently.

Typical uses are processing events per customer, account, device or aggregate: events of one entity
must be handled in order, but events of different entities can be handled in parallel.

## Guarantees

For tasks of the same group:

- a task starts only after the previously submitted task has finished, whether it succeeded or failed
- the effects of a task are visible to the next task of the group (*happens-before*)

Tasks of the same group do **not** necessarily run on the same thread, so don't rely on
`ThreadLocal`s or other thread-confined state being carried over between them.

Waiting tasks do not occupy a thread. A task is handed over to the thread pool only once its
predecessor has finished.

## Requirements

- Java 21 or later
- [SLF4J](https://www.slf4j.org/) 2.x API (the only runtime dependency)

```xml
<dependency>
    <groupId>key-aware-executor</groupId>
    <artifactId>key-aware-executor</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Build and test with `mvn verify`.

## Usage

### Creating an executor

```java
// Dedicated pool (recommended)
var executor = new GroupAwareExecutor(Executors.newVirtualThreadPerTaskExecutor());

// Dedicated pool, blocking submitters while 1000 tasks are pending (see "Limiting pending tasks")
var bounded = new GroupAwareExecutor(Executors.newFixedThreadPool(8), 1000);

// Common ForkJoinPool: only suitable for short, CPU-bound tasks
var shared = new GroupAwareExecutor();
```

A group can be any non-null object that properly implements `equals()` and `hashCode()`, such as a
`String`, a `Long`, a `UUID` or a record.

### Submitting tasks

```java
// Callable with a result
CompletableFuture<Order> order = executor.submit(() -> repository.load(orderId), orderId);

// Runnable without a result
CompletableFuture<Void> done = executor.submit(() -> mailer.send(confirmation), orderId);
```

The result type is chosen per call, so a single executor can run tasks with different result types.
A `Supplier` can be submitted as `executor.submit(supplier::get, group)`.

Tasks can also carry their group themselves by implementing `GroupAwareRunnable`,
`GroupAwareCallable` or `GroupAwareSupplier`:

```java
record OrderEvent(String orderId, String payload) implements GroupAwareRunnable {

    @Override
    public void run() {
        // handle the event
    }

    @Override
    public Object getGroup() {
        return orderId;
    }
}

executor.submit(new OrderEvent("order-42", "created"));
```

`GroupAwareExecutor` also implements `java.util.concurrent.Executor`. `execute()` only accepts
`GroupAwareRunnable`s and throws an `IllegalArgumentException` for any other `Runnable`. As there is
no future to report to, failures of tasks passed to `execute()` are logged.

### Results and failures

`submit` returns a `CompletableFuture`:

- if the task throws an exception, the future completes exceptionally. A checked exception thrown by a
  `Callable` becomes the cause of the failure, like with `ExecutorService`.
- a failing task does not affect the next tasks of its group. They run as usual.
- the returned future is a copy of the future used internally to order the group's tasks.
  Cancelling it or completing it, for example by `orTimeout()`, therefore does **not** stop the
  task or change the execution order of its group.

### Limiting pending tasks

By default, any number of tasks may be pending, i.e. submitted but not yet finished. Constructing
the executor with `maxPendingTasks` limits this number across all groups. Once the limit is
reached, `submit` and `execute` block until a pending task has finished. If the submitting thread is
interrupted while waiting, a `RejectedExecutionException` is thrown and the thread's interrupt flag is
kept set.

> **Note:** with a limit, a task that submits further tasks to the same executor can deadlock,
> if all pending tasks are waiting for the submitting task to finish.

### Shutting down

Tasks waiting for their predecessor are only handed over to the thread pool once the predecessor has
finished. Therefore, shut down the `GroupAwareExecutor` **before** its thread pool, otherwise waiting
tasks are rejected:

```java
executor.shutdown();                              // reject new tasks, finish pending ones
if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
    // pending tasks did not finish in time
}
pool.shutdown();                                  // the executor never shuts down the pool itself
```

`GroupAwareExecutor` is `AutoCloseable`. `close()` shuts it down and waits until all pending tasks have
finished:

```java
try (var pool = Executors.newVirtualThreadPerTaskExecutor();
     var executor = new GroupAwareExecutor(pool)) {
    events.forEach(executor::submit);
}   // closes the executor first, then the pool
```

After `shutdown()`, `submit` and `execute` throw a `RejectedExecutionException`.

### Choosing a thread pool

The no-argument constructor uses `ForkJoinPool.commonPool()`. That pool is shared by the whole JVM,
for example by parallel streams and asynchronous `CompletableFuture` stages, and it is sized for
CPU-bound work. Tasks that block on I/O or locks can starve it. Prefer a dedicated pool:

- `Executors.newVirtualThreadPerTaskExecutor()` for blocking (I/O-bound) tasks
- `Executors.newFixedThreadPool(n)` for CPU-bound tasks or to limit concurrency

The group map entry of a group is removed as soon as the group has no pending tasks, so the number of
distinct groups over time does not grow memory. `getGroupMapSize()` returns the number of groups that
currently have pending tasks.
