package dispatch;

import lombok.SneakyThrows;

import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class GroupAwareSupplierExecutorTest extends AbstractGroupAwareExecutorTest<Result, Supplier<Result>, GroupAwareSupplier<Result>> {

    @Override
    protected AbstractGroupAwareExecutor<Result, Supplier<Result>, GroupAwareSupplier<Result>> newExecutor() {
        return new GroupAwareSupplierExecutor<>(Executors.newFixedThreadPool(5));
    }

    @Override
    protected void produceResult(AbstractGroupAwareExecutor<Result, Supplier<Result>, GroupAwareSupplier<Result>> executor) {
        IntStream.range(0, LOOPS)
                .mapToObj(ignored -> new Task())
                .map(executor::submit)
                .forEach(cf -> cf.thenAccept(results::add));

    }

    private static class Task implements GroupAwareSupplier<Result> {

        private final int group;

        private Task() {
            this.group = random.nextInt(KEY_RANGE);
        }

        @SneakyThrows
        @Override
        public Result get() {
            var count = counter.incrementAndGet();
            Thread.sleep(random.nextInt(MAX_WAIT));
            return new Result(group, count, Thread.currentThread().getName());
        }

        @Override
        public Object getGroup() {
            return group;
        }
    }

}
