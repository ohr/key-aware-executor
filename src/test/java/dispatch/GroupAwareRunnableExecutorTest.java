package dispatch;

import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GroupAwareRunnableExecutorTest {

    private static final List<Result> results = new CopyOnWriteArrayList<>();
    private static final AtomicInteger counter = new AtomicInteger(0);
    private static final Random random = new SecureRandom();

    private static final int KEY_RANGE = 100;
    private static final int MAX_WAIT = 10;
    private static final int LOOPS = 1000;

    @Test
    public void singleThreadedProducer() {
        var executor = new GroupAwareRunnableExecutor(Executors.newFixedThreadPool(5));
        IntStream.range(0, LOOPS)
            .mapToObj(i -> new Task())
            .forEach(executor::execute);
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
            .until(() -> results, hasSize(LOOPS));

        assertThat(executor.getKeyMapSize(), equalTo(0));
        // Check that tasks with the same group have increasing counter values
        var map = new HashMap<Integer, Integer>();
        // results.forEach(System.out::println);
        results.forEach(r ->
            map.merge(r.group(), r.counter(), (current, next) -> {
                assertThat(next, greaterThan(current));
                return next;
            })
        );
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
