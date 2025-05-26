package dispatch;

import lombok.SneakyThrows;

import java.util.function.Supplier;
import java.util.stream.IntStream;

abstract class AbstractGroupAwareSupplierTest extends AbstractGroupAwareExecutorTest<Result, Supplier<Result>, GroupAwareSupplier<Result>> {

    @Override
    protected void produceResult(AbstractGroupAwareExecutor<Result, Supplier<Result>, GroupAwareSupplier<Result>> executor) {
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

    private static class Task implements GroupAwareSupplier<Result> {

        private final int group;

        private Task() {
            this.group = random.nextInt(KEY_RANGE);
        }

        @SneakyThrows
        @Override
        public Result get() {
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
