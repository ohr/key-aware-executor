package dispatch;

import lombok.SneakyThrows;

import java.util.stream.IntStream;

public class GroupAwareRunnableDefaultExecutorTest extends AbstractGroupAwareExecutorTest<Void, Runnable, GroupAwareRunnable> {

    @Override
    protected void produceResult(AbstractGroupAwareExecutor<Void, Runnable, GroupAwareRunnable> executor) {
        IntStream.range(0, LOOPS)
                .mapToObj(ignored -> new Task())
                .forEach(executor::submit);
    }

    @Override
    protected AbstractGroupAwareExecutor<Void, Runnable, GroupAwareRunnable> newExecutor() {
        return new GroupAwareRunnableExecutor();
    }


    private static class Task implements GroupAwareRunnable {

        private final Integer group;

        private Task() {
            this.group = random.nextInt(KEY_RANGE);
        }

        @SneakyThrows
        @Override
        public void run() {
            var count = counter.incrementAndGet();
            Thread.sleep(random.nextInt(MAX_WAIT));
            results.add(new Result(group, count, Thread.currentThread().getName()));
            if (count % 5 == 0) throw new RuntimeException("bla");
        }

        @Override
        public Object getGroup() {
            return group;
        }
    }

}
