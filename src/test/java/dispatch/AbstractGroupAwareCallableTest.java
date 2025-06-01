package dispatch;

import lombok.SneakyThrows;

import java.util.concurrent.Callable;
import java.util.stream.IntStream;

abstract class AbstractGroupAwareCallableTest extends AbstractGroupAwareExecutorTest<Result, Callable<Result>, GroupAwareCallable<Result>> {

    @Override
    protected void produceResult(AbstractGroupAwareExecutor<Result, Callable<Result>, GroupAwareCallable<Result>> executor) {
        IntStream.range(0, LOOPS)
                .mapToObj(ignored -> new Task())
                .map(executor::submit)
                .forEach(cf -> cf
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                failures.incrementAndGet();
                                return false;
                            } else {
                                return results.add(result);
                            }
                        }));
    }

    private static class Task implements GroupAwareCallable<Result> {

        private final int group;

        private Task() {
            this.group = random.nextInt(KEY_RANGE);
        }

        @SneakyThrows(InterruptedException.class)
        @Override
        public Result call() {
            Thread.sleep(random.nextInt(MAX_WAIT));
            var count = counter.incrementAndGet();
            if (count % 5 == 0) {
                throw new RuntimeException("failure at count " + count);
            }
            return new Result(group, count, Thread.currentThread().getName());
        }

        @Override
        public Object getGroup() {
            return group;
        }
    }

}
