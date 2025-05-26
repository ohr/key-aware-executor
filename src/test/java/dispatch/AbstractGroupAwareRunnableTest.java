package dispatch;

import lombok.SneakyThrows;

import java.util.stream.IntStream;

abstract class AbstractGroupAwareRunnableTest extends AbstractGroupAwareExecutorTest<Void, Runnable, GroupAwareRunnable> {

    @Override
    protected void produceResult(AbstractGroupAwareExecutor<Void, Runnable, GroupAwareRunnable> executor) {
        IntStream.range(0, LOOPS)
                .mapToObj(ignored -> new Task())
                .forEach(executor::submit);
    }

    private static class Task implements GroupAwareRunnable {

        private final Integer group;

        private Task() {
            this.group = random.nextInt(KEY_RANGE);
        }

        @SneakyThrows
        @Override
        public void run() {
            Thread.sleep(random.nextInt(MAX_WAIT));
            var count = counter.incrementAndGet();
            if (count % 5 == 0) {
                var failure = failures.incrementAndGet();
                throw new RuntimeException("failure " + failure + " at count " + count);
            }
            results.add(new Result(group, count, Thread.currentThread().getName()));
        }

        @Override
        public Object getGroup() {
            return group;
        }
    }

}
